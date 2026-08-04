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
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ScanEngine(
    private val validatorOverride: Validator? = null,
) {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun scan(proxy: ProxyConfig?, cfg: ScanConfig): Flow<ScanProgress> = channelFlow {
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

        val validator: Validator = validatorOverride
            ?: if (proxy != null) TunnelValidator() else DirectThroughputValidator()

        log("Engine initialized. Mode: ${cfg.mode}")
        if (proxy != null) log("Proxy target: ${proxy.remark} (${proxy.protocol.scheme})")
        if (cfg.sniOverride.isNotEmpty()) log("Using SNI Override: ${cfg.sniOverride}")

        val ports = cfg.ports.ifEmpty { listOf(443) }
        log("Target Ports: ${ports.joinToString(", ")}")
        val probeDispatcher = Dispatchers.IO.limitedParallelism(cfg.concurrency.coerceIn(1, 512))

        val primaryTasks: Iterator<ProbeTask>
        val primaryTotal: Int
        if (cfg.explicitIps.isNotEmpty()) {
            primaryTasks = tasksFor(cfg.explicitIps.asSequence(), ports)
            primaryTotal = cfg.explicitIps.size * ports.size
            log("Running explicit scan on ${cfg.explicitIps.size} targets")
        } else {
            val source = try {
                IpSource.build(
                    useV4 = cfg.useV4,
                    useV6 = cfg.useV6,
                    extra = cfg.extraCidrs,
                    v4Ranges = cfg.customV4Ranges.ifEmpty { com.ahoura.asha_scanner_ip.core.ipsrc.CloudflareRanges.V4 },
                )
            } catch (e: Exception) {
                send(ScanProgress(phase = ScanPhase.ERROR, error = e.message, elapsedMs = elapsed(), logs = snapshotLogs()))
                return@channelFlow
            }
            primaryTasks = tasksFor(source.stream(cfg.count), ports)
            primaryTotal = cfg.count * ports.size
            log("Phase 1: Probing $primaryTotal random targets")
        }

        val healthy = Collections.synchronizedList(ArrayList<ScanResult>())
        val foundCount = AtomicInteger(0)
        val prober = Prober(cfg)
        val latencyTrace = Collections.synchronizedList(ArrayList<Int>())

        // ---- Phase 1: Probing ----
        var grandTotal = primaryTotal
        runProbePhase(primaryTasks, primaryTotal, probeDispatcher, prober, cfg, healthy, foundCount, latencyTrace, ::elapsed, false, ::log, ::snapshotLogs)

        // ---- Phase 1b: Fallback ----
        var usingFallback = false
        if (healthy.isEmpty() && cfg.fallbackToDomains && cfg.fallbackDomains.isNotEmpty()) {
            log("No results in range scan. Attempting open-site fallback...")
            send(ScanProgress(phase = ScanPhase.RESOLVING, elapsedMs = elapsed(), usingFallback = true, latencyTrace = snapshotTrace(latencyTrace), logs = snapshotLogs()))
            val fbHosts = runCatching {
                DomainResolver.resolveHosts(cfg.fallbackDomains, cfg.useV4, cfg.useV6, limit = 400)
            }.getOrDefault(emptyList())
            if (fbHosts.isNotEmpty()) {
                usingFallback = true
                val fbTasks = ArrayList<ProbeTask>(fbHosts.size * ports.size)
                for (h in fbHosts) for (port in ports) fbTasks.add(ProbeTask(h.ip, port, h.domain))
                grandTotal += fbTasks.size
                log("Phase 1b: Probing ${fbTasks.size} domain-derived edges")
                runProbePhase(fbTasks.iterator(), fbTasks.size, probeDispatcher, prober, cfg, healthy, foundCount, latencyTrace, ::elapsed, true, ::log, ::snapshotLogs)
            }
        }

        var results = snapshotBest(healthy, cfg.top)
        if (results.isEmpty()) {
            log("Scan finished: 0 healthy targets found.")
            send(ScanProgress(phase = ScanPhase.DONE, tested = grandTotal, total = grandTotal, elapsedMs = elapsed(), logs = snapshotLogs()))
            return@channelFlow
        }

        // ---- Phase 1.5: Stability Re-check (Simorgh Style) ----
        log("Phase 1.5: Re-checking stability for top ${results.size} candidates")
        val stabilityCount = cfg.stabilityCount 
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
                        val check = prober.probe(r.ip, r.port, if (usingFallback) r.ip else null)
                        if (check.healthy) {
                            success++
                            samples.addAll(check.latenciesMs)
                        }
                        // Variable delay to catch rate-limiting/DPI behaviors
                        kotlinx.coroutines.delay(50L + java.util.Random().nextInt(100))
                    }
                    val passRate = success.toDouble() / stabilityCount
                    stableResults.add(r.copy(passRate = passRate, latenciesMs = samples))
                    val d = sDone.incrementAndGet()
                    val current = synchronized(stableResults) { 
                        ArrayList(stableResults).sortedWith(
                            compareByDescending<ScanResult> { it.passRate }
                                .thenBy { it.jitterMs } // Prioritize low jitter for stability
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
            ArrayList(stableResults).filter { it.passRate > 0 }.sortedWith(compareByDescending<ScanResult> { it.passRate }.thenBy { it.avgLatencyMs }).take(cfg.top) 
        }
        log("Stability check complete. ${results.size} survivors.")

        // ---- Phase 2: Speed Test ----
        if (!cfg.speedTest || results.isEmpty()) {
            log("Scan finished. Results: ${results.size}")
            send(ScanProgress(phase = ScanPhase.DONE, tested = grandTotal, total = grandTotal, found = foundCount.get(), elapsedMs = elapsed(), best = results, usingFallback = usingFallback, logs = snapshotLogs()))
            return@channelFlow
        }

        log("Phase 2: Running throughput validation on top ${results.size} survivors")
        val validated = Collections.synchronizedList(ArrayList<ScanResult>())
        val vDone = AtomicInteger(0)
        val vIdx = AtomicInteger(0)
        val vTotal = results.size
        
        send(ScanProgress(phase = ScanPhase.VALIDATING, tested = grandTotal, total = grandTotal, found = foundCount.get(), validated = 0, validateTotal = vTotal, elapsedMs = elapsed(), best = results, usingFallback = usingFallback, logs = snapshotLogs()))

        coroutineScope {
            val vConcurrency = 4.coerceAtMost(vTotal).coerceAtLeast(1)
            (0 until vConcurrency).map {
                launch(Dispatchers.IO) {
                    while (isActive) {
                        val i = vIdx.getAndIncrement()
                        if (i >= vTotal) break
                        val r = results[i]
                        log("Testing throughput: ${r.ip}...")
                        val enriched = validator.validate(r, proxy, cfg)
                        validated.add(enriched)
                        val d = vDone.incrementAndGet()
                        val current = synchronized(validated) { ResultSort.bySpeed(ArrayList(validated)) }
                        send(ScanProgress(phase = ScanPhase.VALIDATING, tested = grandTotal, total = grandTotal, found = foundCount.get(), validated = d, validateTotal = vTotal, elapsedMs = elapsed(), best = current, usingFallback = usingFallback, logs = snapshotLogs()))
                    }
                }
            }.joinAll()
        }

        val finalBest = synchronized(validated) { ResultSort.bySpeed(ArrayList(validated)) }
        log("Scan complete. Best speed: ${String.format(java.util.Locale.US, "%.2f", finalBest.firstOrNull()?.throughputMbps ?: 0.0)} Mbps")
        send(ScanProgress(phase = ScanPhase.DONE, tested = grandTotal, total = grandTotal, found = foundCount.get(), validated = vDone.get(), validateTotal = vTotal, elapsedMs = elapsed(), best = finalBest, usingFallback = usingFallback, logs = snapshotLogs()))
    }

    private companion object {
        const val TRACE_CAP = 72
    }

    private data class ProbeTask(val ip: String, val port: Int, val sni: String? = null)

    private fun tasksFor(ips: Sequence<String>, ports: List<Int>): Iterator<ProbeTask> =
        ips.flatMap { ip -> ports.asSequence().map { ProbeTask(ip, it) } }.iterator()

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
        taskIter: Iterator<ProbeTask>,
        total: Int,
        dispatcher: CoroutineDispatcher,
        prober: Prober,
        cfg: ScanConfig,
        healthy: MutableList<ScanResult>,
        foundCount: AtomicInteger,
        latencyTrace: MutableList<Int>,
        elapsed: () -> Long,
        usingFallback: Boolean,
        log: (String) -> Unit,
        snapshotLogs: () -> List<String>
    ) {
        if (total == 0) return
        val tested = AtomicInteger(0)
        val lastEmit = java.util.concurrent.atomic.AtomicLong(0)
        val stopEarly = java.util.concurrent.atomic.AtomicBoolean(false)
        val earlyTarget = maxOf(cfg.top * 4, 30)
        val canStopEarly = cfg.smartStop && cfg.explicitIps.isEmpty()

        coroutineScope {
            (0 until cfg.concurrency.coerceAtLeast(1)).map {
                launch(dispatcher) {
                    while (isActive && !stopEarly.get()) {
                        val task = synchronized(taskIter) { if (taskIter.hasNext()) taskIter.next() else null } ?: break
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
                        val last = lastEmit.get()
                        if (now - last >= 100 || t == total || stopEarly.get()) {
                            if (lastEmit.compareAndSet(last, now) || t == total || stopEarly.get()) {
                                send(ScanProgress(
                                    phase = ScanPhase.PROBING, tested = t, total = total, found = foundCount.get(),
                                    elapsedMs = elapsed(), best = snapshotBest(healthy, cfg.top),
                                    usingFallback = usingFallback, latencyTrace = snapshotTrace(latencyTrace),
                                    logs = snapshotLogs()
                                ))
                            }
                        }
                    }
                }
            }.joinAll()
        }
    }
}
