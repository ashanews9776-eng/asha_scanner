package com.ahoura.asha_scanner_ip.core.engine

import com.ahoura.asha_scanner_ip.core.ipsrc.DomainResolver
import com.ahoura.asha_scanner_ip.core.ipsrc.IpSource
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanPhase
import com.ahoura.asha_scanner_ip.core.model.ScanProgress
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.prober.Prober
import com.ahoura.asha_scanner_ip.core.validator.DirectThroughputValidator
import com.ahoura.asha_scanner_ip.core.validator.TunnelValidator
import com.ahoura.asha_scanner_ip.core.validator.Validator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ScanEngine(
    private val validatorOverride: Validator? = null,
) {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun scan(
        proxy: ProxyConfig?,
        cfg: ScanConfig,
        resumeSnapshot: ScanSnapshot? = null,
    ): Flow<ScanProgress> = channelFlow {
        val startMs = System.currentTimeMillis()
        fun elapsed() = System.currentTimeMillis() - startMs

        val logs = Collections.synchronizedList(ArrayList<String>())
        fun log(msg: String) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            synchronized(logs) {
                logs.add("[$timestamp] $msg")
                if (logs.size > 50) logs.removeAt(0)
            }
        }

        fun snapshotLogs() = synchronized(logs) { ArrayList(logs) }

        // Apply Gentle Mode overrides: max 25 concurrency, min 6s timeout, 40 probes/s rate limit
        val effectiveConcurrency = if (cfg.gentle) cfg.concurrency.coerceIn(1, 25) else cfg.concurrency.coerceIn(1, 512)
        val effectiveTimeoutMs = if (cfg.gentle) maxOf(cfg.timeoutMs, 6000L) else cfg.timeoutMs
        val effectiveCfg = cfg.copy(concurrency = effectiveConcurrency, timeoutMs = effectiveTimeoutMs)

        val rateLimiter = if (effectiveCfg.gentle) RateLimiter(40.0) else null

        val validator: Validator = validatorOverride
            ?: if (proxy != null) TunnelValidator() else DirectThroughputValidator()

        log("Engine initialized. Mode: ${effectiveCfg.mode}${if (effectiveCfg.gentle) " [GENTLE: 25w / 40pps / 6s]" else ""}")
        if (proxy != null) log("Proxy target: ${proxy.remark} (${proxy.protocol.scheme})")
        if (effectiveCfg.sniOverride.isNotEmpty()) log("Using SNI Override: ${effectiveCfg.sniOverride}")
        if (effectiveCfg.antiDpi.enabled) log("Anti-DPI TLS fragmentation active")

        val ports = effectiveCfg.ports.ifEmpty { listOf(443) }
        log("Target Ports: ${ports.joinToString(", ")}")
        val probeDispatcher = Dispatchers.IO.limitedParallelism(effectiveConcurrency)

        val healthy = Collections.synchronizedList(ArrayList<ScanResult>())
        val foundCount = AtomicInteger(0)
        val latencyTrace = Collections.synchronizedList(ArrayList<Int>())

        val primaryTasks: List<ProbeTask>
        val primaryTotal: Int
        var initialTested = 0

        if (resumeSnapshot != null) {
            log("Resuming previous scan: restoring ${resumeSnapshot.healthyResults.size} healthy results and ${resumeSnapshot.remainingTasks.size} remaining tasks")
            healthy.addAll(resumeSnapshot.healthyResults)
            foundCount.set(resumeSnapshot.healthyResults.size)
            initialTested = resumeSnapshot.testedCount
            primaryTasks = resumeSnapshot.remainingTasks.map { (ip, port) -> ProbeTask(ip, port) }
            primaryTotal = resumeSnapshot.totalCount

            // Immediately emit progress so restored healthy results appear in results table right away
            send(
                ScanProgress(
                    phase = ScanPhase.PROBING,
                    tested = initialTested,
                    total = primaryTotal,
                    found = healthy.size,
                    best = snapshotBest(healthy, effectiveCfg.top),
                    elapsedMs = 0L,
                    logs = snapshotLogs(),
                )
            )
        } else if (effectiveCfg.explicitIps.isNotEmpty()) {
            val list = mutableListOf<ProbeTask>()
            for (ip in effectiveCfg.explicitIps) {
                for (port in ports) list.add(ProbeTask(ip, port))
            }
            primaryTasks = list
            primaryTotal = list.size
            log("Running explicit scan on ${effectiveCfg.explicitIps.size} targets ($primaryTotal probes)")
        } else {
            val source = try {
                IpSource.build(
                    useV4 = effectiveCfg.useV4,
                    useV6 = effectiveCfg.useV6,
                    extra = effectiveCfg.extraCidrs,
                    v4Ranges = effectiveCfg.customV4Ranges.ifEmpty { com.ahoura.asha_scanner_ip.core.ipsrc.CloudflareRanges.V4 },
                )
            } catch (e: Exception) {
                send(ScanProgress(phase = ScanPhase.ERROR, error = e.message, elapsedMs = elapsed(), logs = snapshotLogs()))
                return@channelFlow
            }
            val ips = source.stream(effectiveCfg.count).toList()
            val list = mutableListOf<ProbeTask>()
            for (ip in ips) {
                for (port in ports) list.add(ProbeTask(ip, port))
            }
            primaryTasks = list
            primaryTotal = list.size
            log("Phase 1: Probing $primaryTotal random targets")
        }

        // ---- Phase 2 Only: Skip Reachability Scan ----
        if (effectiveCfg.phase2Only) {
            log("Skipping Phase 1 reachability probing (Phase 2 direct throughput mode)")
            val candidates = primaryTasks.map { task ->
                ScanResult(
                    ip = task.ip,
                    port = task.port,
                    mode = effectiveCfg.mode,
                    latenciesMs = emptyList(),
                    attempts = 1,
                    healthy = true,
                )
            }
            runSpeedTestPhase(
                candidates = candidates,
                validator = validator,
                proxy = proxy,
                cfg = effectiveCfg,
                grandTotal = primaryTotal,
                foundCount = primaryTotal,
                elapsed = ::elapsed,
                usingFallback = false,
                log = ::log,
                snapshotLogs = ::snapshotLogs,
            )
            ScanStateStore.clear()
            return@channelFlow
        }

        val prober = Prober(effectiveCfg)

        // ---- Phase 1: Probing ----
        var grandTotal = primaryTotal
        runProbePhase(
            taskList = primaryTasks,
            initialTested = initialTested,
            total = grandTotal,
            dispatcher = probeDispatcher,
            prober = prober,
            cfg = effectiveCfg,
            rateLimiter = rateLimiter,
            healthy = healthy,
            foundCount = foundCount,
            latencyTrace = latencyTrace,
            elapsed = ::elapsed,
            usingFallback = false,
            log = ::log,
            snapshotLogs = ::snapshotLogs,
        )

        // ---- Phase 1b: Fallback ----
        var usingFallback = false
        if (healthy.isEmpty() && effectiveCfg.fallbackToDomains && effectiveCfg.fallbackDomains.isNotEmpty()) {
            log("No results in range scan. Attempting open-site fallback...")
            send(ScanProgress(phase = ScanPhase.RESOLVING, elapsedMs = elapsed(), usingFallback = true, latencyTrace = snapshotTrace(latencyTrace), logs = snapshotLogs()))
            val fbHosts = runCatching {
                DomainResolver.resolveHosts(effectiveCfg.fallbackDomains, effectiveCfg.useV4, effectiveCfg.useV6, limit = 400)
            }.getOrDefault(emptyList())
            if (fbHosts.isNotEmpty()) {
                usingFallback = true
                val fbTasks = ArrayList<ProbeTask>(fbHosts.size * ports.size)
                for (h in fbHosts) for (port in ports) fbTasks.add(ProbeTask(h.ip, port, h.domain))
                grandTotal += fbTasks.size
                log("Phase 1b: Probing ${fbTasks.size} domain-derived edges")
                runProbePhase(
                    taskList = fbTasks,
                    initialTested = 0,
                    total = fbTasks.size,
                    dispatcher = probeDispatcher,
                    prober = prober,
                    cfg = effectiveCfg,
                    rateLimiter = rateLimiter,
                    healthy = healthy,
                    foundCount = foundCount,
                    latencyTrace = latencyTrace,
                    elapsed = ::elapsed,
                    usingFallback = true,
                    log = ::log,
                    snapshotLogs = ::snapshotLogs,
                )
            }
        }

        var results = snapshotBest(healthy, effectiveCfg.top)
        if (results.isEmpty()) {
            log("Scan finished: 0 healthy targets found.")
            ScanStateStore.clear()
            send(ScanProgress(phase = ScanPhase.DONE, tested = grandTotal, total = grandTotal, elapsedMs = elapsed(), logs = snapshotLogs()))
            return@channelFlow
        }

        // ---- Phase 1.5: Stability Re-check ----
        log("Phase 1.5: Re-checking stability for top ${results.size} candidates")
        val stabilityCount = effectiveCfg.stabilityCount
        val stableResults = Collections.synchronizedList(ArrayList<ScanResult>())
        val sDone = AtomicInteger(0)
        val sTotal = results.size

        send(ScanProgress(
            phase = ScanPhase.STABILITY, tested = grandTotal, total = grandTotal,
            found = foundCount.get(), validated = 0, validateTotal = sTotal,
            elapsedMs = elapsed(), best = results, usingFallback = usingFallback, logs = snapshotLogs()
        ))

        coroutineScope {
            results.forEach { r ->
                launch(Dispatchers.IO) {
                    var success = 0
                    val samples = ArrayList<Long>()
                    repeat(stabilityCount) {
                        rateLimiter?.acquire()
                        val check = prober.probe(r.ip, r.port, if (usingFallback) r.ip else null)
                        if (check.healthy) {
                            success++
                            samples.addAll(check.latenciesMs)
                        }
                        kotlinx.coroutines.delay(50L + java.util.Random().nextInt(100))
                    }
                    val passRate = success.toDouble() / stabilityCount
                    stableResults.add(r.copy(passRate = passRate, latenciesMs = samples))
                    val d = sDone.incrementAndGet()
                    val current = synchronized(stableResults) {
                        ArrayList(stableResults).sortedWith(
                            compareByDescending<ScanResult> { it.passRate }
                                .thenBy { it.jitterMs }
                                .thenBy { it.avgLatencyMs }
                        )
                    }
                    send(ScanProgress(
                        phase = ScanPhase.STABILITY, tested = grandTotal, total = grandTotal,
                        found = foundCount.get(), validated = d, validateTotal = sTotal,
                        elapsedMs = elapsed(), best = current, usingFallback = usingFallback, logs = snapshotLogs()
                    ))
                }
            }
        }
        results = synchronized(stableResults) {
            ArrayList(stableResults).filter { it.passRate > 0 }.sortedWith(compareByDescending<ScanResult> { it.passRate }.thenBy { it.avgLatencyMs }).take(effectiveCfg.top)
        }
        log("Stability check complete. ${results.size} survivors.")

        // ---- Phase 2: Speed Test ----
        if (!effectiveCfg.speedTest || results.isEmpty()) {
            log("Scan finished. Results: ${results.size}")
            ScanStateStore.clear()
            send(ScanProgress(phase = ScanPhase.DONE, tested = grandTotal, total = grandTotal, found = foundCount.get(), elapsedMs = elapsed(), best = results, usingFallback = usingFallback, logs = snapshotLogs()))
            return@channelFlow
        }

        runSpeedTestPhase(
            candidates = results,
            validator = validator,
            proxy = proxy,
            cfg = effectiveCfg,
            grandTotal = grandTotal,
            foundCount = foundCount.get(),
            elapsed = ::elapsed,
            usingFallback = usingFallback,
            log = ::log,
            snapshotLogs = ::snapshotLogs,
        )
        ScanStateStore.clear()
    }

    private companion object {
        const val TRACE_CAP = 72
    }

    data class ProbeTask(val ip: String, val port: Int, val sni: String? = null)

    private fun snapshotBest(healthy: List<ScanResult>, top: Int): List<ScanResult> =
        synchronized(healthy) { ResultSort.topByLatency(ArrayList(healthy), top) }

    private fun snapshotTrace(trace: List<Int>): List<Int> =
        synchronized(trace) { ArrayList(trace) }

    private fun recordSample(trace: MutableList<Int>, r: ScanResult) {
        val v = if (r.latenciesMs.isNotEmpty()) r.avgLatencyMs.toInt().coerceAtLeast(1) else 0
        synchronized(trace) {
            trace.add(v)
            while (trace.size > TRACE_CAP) trace.removeAt(0)
        }
    }

    private suspend fun ProducerScope<ScanProgress>.runProbePhase(
        taskList: List<ProbeTask>,
        initialTested: Int,
        total: Int,
        dispatcher: CoroutineDispatcher,
        prober: Prober,
        cfg: ScanConfig,
        rateLimiter: RateLimiter?,
        healthy: MutableList<ScanResult>,
        foundCount: AtomicInteger,
        latencyTrace: MutableList<Int>,
        elapsed: () -> Long,
        usingFallback: Boolean,
        log: (String) -> Unit,
        snapshotLogs: () -> List<String>,
    ) {
        if (taskList.isEmpty()) return
        val taskIndex = AtomicInteger(0)
        val tested = AtomicInteger(initialTested)
        val lastEmit = AtomicLong(0)
        val lastSnapshotSave = AtomicLong(System.currentTimeMillis())
        val stopEarly = AtomicBoolean(false)
        val earlyTarget = maxOf(cfg.top * 4, 30)
        val canStopEarly = cfg.smartStop && cfg.explicitIps.isEmpty()

        coroutineScope {
            (0 until cfg.concurrency.coerceAtLeast(1)).map {
                launch(dispatcher) {
                    while (isActive && !stopEarly.get()) {
                        val idx = taskIndex.getAndIncrement()
                        if (idx >= taskList.size) break
                        val task = taskList[idx]

                        rateLimiter?.acquire()
                        val r = prober.probe(task.ip, task.port, task.sni)
                        val t = tested.incrementAndGet()
                        recordSample(latencyTrace, r)
                        if (r.healthy) {
                            healthy.add(r)
                            val f = foundCount.incrementAndGet()
                            if (canStopEarly && f >= earlyTarget) {
                                log("Found $f healthy IPs. Smart-stopping primary phase.")
                                stopEarly.set(true)
                            }
                        }

                        val now = System.currentTimeMillis()

                        // Periodic snapshot saving every 20 seconds
                        if (now - lastSnapshotSave.get() >= 20_000) {
                            if (lastSnapshotSave.compareAndSet(lastSnapshotSave.get(), now)) {
                                val remainingStart = taskIndex.get().coerceAtMost(taskList.size)
                                val remaining = taskList.subList(remainingStart, taskList.size).map { it.ip to it.port }
                                ScanStateStore.save(
                                    ScanSnapshot(
                                        config = cfg,
                                        healthyResults = synchronized(healthy) { ArrayList(healthy) },
                                        testedCount = t,
                                        totalCount = total,
                                        remainingTasks = remaining,
                                    )
                                )
                            }
                        }

                        val last = lastEmit.get()
                        if (now - last >= 100 || t == total || stopEarly.get()) {
                            if (lastEmit.compareAndSet(last, now) || t == total || stopEarly.get()) {
                                send(
                                    ScanProgress(
                                        phase = ScanPhase.PROBING,
                                        tested = t,
                                        total = total,
                                        found = foundCount.get(),
                                        elapsedMs = elapsed(),
                                        best = snapshotBest(healthy, cfg.top),
                                        usingFallback = usingFallback,
                                        latencyTrace = snapshotTrace(latencyTrace),
                                        logs = snapshotLogs(),
                                    )
                                )
                            }
                        }
                    }
                }
            }.joinAll()
        }
    }

    private suspend fun ProducerScope<ScanProgress>.runSpeedTestPhase(
        candidates: List<ScanResult>,
        validator: Validator,
        proxy: ProxyConfig?,
        cfg: ScanConfig,
        grandTotal: Int,
        foundCount: Int,
        elapsed: () -> Long,
        usingFallback: Boolean,
        log: (String) -> Unit,
        snapshotLogs: () -> List<String>,
    ) {
        log("Phase 2: Running throughput validation on ${candidates.size} targets")
        val pool = Collections.synchronizedList(ArrayList(candidates))
        val vDone = AtomicInteger(0)
        val vIdx = AtomicInteger(0)
        val vTotal = candidates.size

        send(
            ScanProgress(
                phase = ScanPhase.VALIDATING,
                tested = grandTotal,
                total = grandTotal,
                found = foundCount,
                validated = 0,
                validateTotal = vTotal,
                elapsedMs = elapsed(),
                best = ArrayList(pool),
                usingFallback = usingFallback,
                logs = snapshotLogs(),
            )
        )

        coroutineScope {
            val vConcurrency = 4.coerceAtMost(vTotal).coerceAtLeast(1)
            (0 until vConcurrency).map {
                launch(Dispatchers.IO) {
                    while (isActive) {
                        val i = vIdx.getAndIncrement()
                        if (i >= vTotal) break
                        val orig = pool[i]
                        log("Testing throughput: ${orig.ip}...")
                        val enriched = validator.validate(orig, proxy, cfg)
                        synchronized(pool) {
                            pool[i] = enriched
                        }
                        val d = vDone.incrementAndGet()
                        val currentBest = synchronized(pool) {
                            ResultSort.bySpeed(ArrayList(pool))
                        }
                        send(
                            ScanProgress(
                                phase = ScanPhase.VALIDATING,
                                tested = grandTotal,
                                total = grandTotal,
                                found = foundCount,
                                validated = d,
                                validateTotal = vTotal,
                                elapsedMs = elapsed(),
                                best = currentBest,
                                usingFallback = usingFallback,
                                logs = snapshotLogs(),
                            )
                        )
                    }
                }
            }.joinAll()
        }

        val finalBest = synchronized(pool) { ResultSort.bySpeed(ArrayList(pool)) }
        log("Scan complete. Best speed: ${String.format(java.util.Locale.US, "%.2f", finalBest.firstOrNull()?.throughputMbps ?: 0.0)} Mbps")
        send(
            ScanProgress(
                phase = ScanPhase.DONE,
                tested = grandTotal,
                total = grandTotal,
                found = foundCount,
                validated = vDone.get(),
                validateTotal = vTotal,
                elapsedMs = elapsed(),
                best = finalBest,
                usingFallback = usingFallback,
                logs = snapshotLogs(),
            )
        )
    }

    /**
     * Rate limiter for Gentle mode: strictly enforces max permits per second
     * across all concurrent workers without blocking threads.
     */
    class RateLimiter(private val ratePerSec: Double) {
        private val mutex = Mutex()
        private var lastTime = System.nanoTime()
        private var permits = ratePerSec

        suspend fun acquire() {
            var waitMs = 0L
            mutex.withLock {
                val now = System.nanoTime()
                val elapsedSec = (now - lastTime) / 1_000_000_000.0
                lastTime = now
                permits = (permits + elapsedSec * ratePerSec).coerceAtMost(ratePerSec)
                if (permits < 1.0) {
                    val waitSec = (1.0 - permits) / ratePerSec
                    waitMs = (waitSec * 1000).toLong().coerceAtLeast(1)
                    permits = 0.0
                    lastTime = now + waitMs * 1_000_000
                } else {
                    permits -= 1.0
                }
            }
            if (waitMs > 0) kotlinx.coroutines.delay(waitMs)
        }
    }
}
