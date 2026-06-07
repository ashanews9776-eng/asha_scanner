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

/**
 * Orchestrates a full scan: Phase-1 probing fanned out across a worker pool,
 * then Phase-2 throughput validation of the best survivors. Emits a stream of
 * [ScanProgress] snapshots that the UI renders live.
 *
 * Mirrors SenPaiScanner's engine flow (probe -> rank -> validate -> rank), with
 * an added open-site fallback: if Phase-1 finds nothing, IPs resolved from
 * Cloudflare-fronted domains are probed before giving up.
 */
class ScanEngine(
    // Explicit override (e.g. an xray-core backed validator). When null the engine
    // picks per-scan: tunnel-through-config when the user pasted a proxy, else a
    // direct edge measurement.
    private val validatorOverride: Validator? = null,
) {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun scan(proxy: ProxyConfig?, cfg: ScanConfig): Flow<ScanProgress> = channelFlow {
        val startMs = System.currentTimeMillis()
        fun elapsed() = System.currentTimeMillis() - startMs

        val validator: Validator = validatorOverride
            ?: if (proxy != null) TunnelValidator() else DirectThroughputValidator()

        val ports = cfg.ports.ifEmpty { listOf(443) }
        // Map the worker count onto real threads. Dispatchers.IO alone soft-caps
        // at 64 threads, so blocking socket probes never parallelise past that no
        // matter how many workers we launch; a per-scan limitedParallelism view
        // lets a "200 workers" setting actually run 200 concurrent dials.
        val probeDispatcher = Dispatchers.IO.limitedParallelism(cfg.concurrency.coerceIn(1, 512))

        // Build the Phase-1 task stream lazily: candidate IPs are generated on
        // demand (never fully materialised) and expanded across ports as workers
        // pull them, so even multi-million-IP counts stay light on memory.
        val primaryTasks: Iterator<ProbeTask>
        val primaryTotal: Int
        if (cfg.explicitIps.isNotEmpty()) {
            primaryTasks = tasksFor(cfg.explicitIps.asSequence(), ports)
            primaryTotal = cfg.explicitIps.size * ports.size
        } else {
            val source = try {
                IpSource.build(
                    useV4 = cfg.useV4,
                    useV6 = cfg.useV6,
                    extra = cfg.extraCidrs,
                    v4Ranges = cfg.customV4Ranges.ifEmpty { com.ahoura.asha_scanner_ip.core.ipsrc.CloudflareRanges.V4 },
                )
            } catch (e: Exception) {
                send(ScanProgress(phase = ScanPhase.ERROR, error = e.message, elapsedMs = elapsed()))
                return@channelFlow
            }
            primaryTasks = tasksFor(source.stream(cfg.count), ports)
            primaryTotal = cfg.count * ports.size
        }

        val healthy = Collections.synchronizedList(ArrayList<ScanResult>())
        val foundCount = AtomicInteger(0)
        val prober = Prober(cfg)

        // ---- Phase 1: probe the primary candidates -------------------------
        var grandTotal = primaryTotal
        runProbePhase(primaryTasks, primaryTotal, probeDispatcher, prober, cfg, healthy, foundCount, ::elapsed, usingFallback = false)

        // ---- Phase 1b: open-site fallback ----------------------------------
        // Nothing healthy from the range scan? Resolve known Cloudflare-fronted
        // domains and probe their edge IPs — those are addresses the network is
        // demonstrably letting through for sites that still load.
        var usingFallback = false
        if (healthy.isEmpty() && cfg.fallbackToDomains && cfg.fallbackDomains.isNotEmpty()) {
            send(ScanProgress(phase = ScanPhase.RESOLVING, elapsedMs = elapsed(), usingFallback = true))
            val fbHosts = runCatching {
                DomainResolver.resolveHosts(
                    domains = cfg.fallbackDomains,
                    useV4 = cfg.useV4,
                    useV6 = cfg.useV6,
                    limit = 400,
                )
            }.getOrDefault(emptyList())
            if (fbHosts.isNotEmpty()) {
                usingFallback = true
                // Probe each domain-derived edge with that domain as its SNI.
                val fbTasks = ArrayList<ProbeTask>(fbHosts.size * ports.size)
                for (h in fbHosts) for (port in ports) fbTasks.add(ProbeTask(h.ip, port, h.domain))
                grandTotal += fbTasks.size
                runProbePhase(fbTasks.iterator(), fbTasks.size, probeDispatcher, prober, cfg, healthy, foundCount, ::elapsed, usingFallback = true)
            }
        }

        val phase1Best = snapshotBest(healthy, cfg.top)

        // ---- Phase 2: validate throughput of the best candidates -----------
        if (!cfg.speedTest || phase1Best.isEmpty()) {
            send(
                ScanProgress(
                    phase = ScanPhase.DONE,
                    tested = grandTotal, total = grandTotal,
                    found = foundCount.get(),
                    elapsedMs = elapsed(),
                    best = phase1Best,
                    usingFallback = usingFallback,
                )
            )
            return@channelFlow
        }

        val validated = Collections.synchronizedList(ArrayList<ScanResult>())
        val vDone = AtomicInteger(0)
        val vIdx = AtomicInteger(0)
        val vTotal = phase1Best.size
        send(
            ScanProgress(
                phase = ScanPhase.VALIDATING,
                tested = grandTotal, total = grandTotal,
                found = foundCount.get(),
                validated = 0, validateTotal = vTotal,
                elapsedMs = elapsed(), best = phase1Best,
                usingFallback = usingFallback,
            )
        )

        coroutineScope {
            val vConcurrency = 4.coerceAtMost(vTotal).coerceAtLeast(1)
            val workers = (0 until vConcurrency).map {
                launch(Dispatchers.IO) {
                    while (isActive) {
                        val i = vIdx.getAndIncrement()
                        if (i >= vTotal) break
                        val enriched = validator.validate(phase1Best[i], proxy, cfg)
                        validated.add(enriched)
                        val d = vDone.incrementAndGet()
                        val best = synchronized(validated) {
                            ResultSort.bySpeed(ArrayList(validated))
                        }
                        send(
                            ScanProgress(
                                phase = ScanPhase.VALIDATING,
                                tested = grandTotal, total = grandTotal,
                                found = foundCount.get(),
                                validated = d, validateTotal = vTotal,
                                elapsedMs = elapsed(), best = best,
                                usingFallback = usingFallback,
                            )
                        )
                    }
                }
            }
            workers.joinAll()
        }

        val finalBest = synchronized(validated) { ResultSort.bySpeed(ArrayList(validated)) }
        send(
            ScanProgress(
                phase = ScanPhase.DONE,
                tested = grandTotal, total = grandTotal,
                found = foundCount.get(),
                validated = vDone.get(), validateTotal = vTotal,
                elapsedMs = elapsed(),
                best = finalBest,
                usingFallback = usingFallback,
            )
        )
    }

    /** One probe unit: an endpoint plus an optional SNI to pin (null = rotate). */
    private data class ProbeTask(val ip: String, val port: Int, val sni: String? = null)

    /** Lazily expand candidate IPs × ports into a flat probe-task iterator. */
    private fun tasksFor(ips: Sequence<String>, ports: List<Int>): Iterator<ProbeTask> =
        ips.flatMap { ip -> ports.asSequence().map { ProbeTask(ip, it) } }.iterator()

    private fun snapshotBest(healthy: List<ScanResult>, top: Int): List<ScanResult> =
        synchronized(healthy) { ResultSort.topByLatency(ArrayList(healthy), top) }

    /**
     * Run one Phase-1 probing pass over [tasks], appending healthy hits to the
     * shared [healthy] list and emitting throttled [ScanProgress] snapshots.
     * Used twice: once for the primary candidates, once for the open-site
     * fallback (distinguished by [usingFallback]).
     */
    private suspend fun ProducerScope<ScanProgress>.runProbePhase(
        taskIter: Iterator<ProbeTask>,
        total: Int,
        dispatcher: CoroutineDispatcher,
        prober: Prober,
        cfg: ScanConfig,
        healthy: MutableList<ScanResult>,
        foundCount: AtomicInteger,
        elapsed: () -> Long,
        usingFallback: Boolean,
    ) {
        if (total == 0) return
        val tested = AtomicInteger(0)
        val lastEmit = java.util.concurrent.atomic.AtomicLong(0)
        val stopEarly = java.util.concurrent.atomic.AtomicBoolean(false)

        // Smart-stop target: once we have comfortably more than we need, there's
        // no point probing thousands more IPs. Scales with the user's "keep best".
        val earlyTarget = maxOf(cfg.top * 4, 30)

        send(
            ScanProgress(
                phase = ScanPhase.PROBING, total = total,
                found = foundCount.get(), elapsedMs = elapsed(),
                best = snapshotBest(healthy, cfg.top), usingFallback = usingFallback,
            )
        )

        coroutineScope {
            val workers = (0 until cfg.concurrency.coerceAtLeast(1)).map {
                launch(dispatcher) {
                    while (isActive && !stopEarly.get()) {
                        // Pull the next task from the shared lazy iterator. The IP
                        // generator/iterator isn't thread-safe, so guard the step.
                        val task = synchronized(taskIter) {
                            if (taskIter.hasNext()) taskIter.next() else null
                        } ?: break
                        val r = prober.probe(task.ip, task.port, task.sni)
                        val t = tested.incrementAndGet()
                        if (r.healthy) {
                            healthy.add(r)
                            val f = foundCount.incrementAndGet()
                            if (cfg.smartStop && f >= earlyTarget) stopEarly.set(true)
                        }
                        // Throttle UI emissions to ~10/sec to keep recomposition cheap,
                        // but always emit the final probe and stop-triggering events.
                        val now = System.currentTimeMillis()
                        val last = lastEmit.get()
                        val due = now - last >= 100
                        if (due || t == total || stopEarly.get()) {
                            if (lastEmit.compareAndSet(last, now) || t == total || stopEarly.get()) {
                                send(
                                    ScanProgress(
                                        phase = ScanPhase.PROBING,
                                        tested = t, total = total,
                                        found = foundCount.get(),
                                        elapsedMs = elapsed(),
                                        best = snapshotBest(healthy, cfg.top),
                                        usingFallback = usingFallback,
                                    )
                                )
                            }
                        }
                    }
                }
            }
            workers.joinAll()
        }
    }
}
