package com.ahoura.asha_scanner_ip.core.engine

import com.ahoura.asha_scanner_ip.core.model.ScanResult

/** User-selectable ranking keys (mirrors result.go's sort modes). */
enum class SortKey { LATENCY, SPEED, JITTER, LOSS }

/** Sorting strategies, ported from result.go's comparators. */
object ResultSort {

    /** Apply a user-selected [key] to an already-collected result list. */
    fun by(results: List<ScanResult>, key: SortKey): List<ScanResult> = when (key) {
        SortKey.LATENCY -> byLatency(results)
        SortKey.SPEED -> bySpeed(results)
        SortKey.JITTER -> results.sortedWith(
            compareByDescending<ScanResult> { it.healthy }.thenBy { it.jitterMs }.thenBy { it.ip }
        )
        SortKey.LOSS -> results.sortedWith(
            compareByDescending<ScanResult> { it.healthy }
                .thenBy { it.loss }
                .thenBy { if (it.avgLatencyMs <= 0) Double.MAX_VALUE else it.avgLatencyMs }
                .thenBy { it.ip }
        )
    }

    /** Healthy first, then lowest average latency. Used during Phase 1. */
    fun byLatency(results: List<ScanResult>): List<ScanResult> =
        results.sortedWith(
            compareByDescending<ScanResult> { it.healthy }
                .thenBy { if (it.avgLatencyMs <= 0) Double.MAX_VALUE else it.avgLatencyMs }
                .thenByDescending { it.tlsOk }
                .thenBy { it.ip }
        )

    /** Healthy first, then highest throughput, then latency. Used for final ranking. */
    fun bySpeed(results: List<ScanResult>): List<ScanResult> =
        results.sortedWith(
            compareByDescending<ScanResult> { it.healthy }
                .thenByDescending { it.throughputBytesPerSec }
                .thenBy { if (it.avgLatencyMs <= 0) Double.MAX_VALUE else it.avgLatencyMs }
                .thenBy { it.ip }
        )

    /** Top [n] healthy results by latency (ignoring unhealthy). */
    fun topByLatency(results: List<ScanResult>, n: Int): List<ScanResult> =
        byLatency(results.filter { it.healthy }).take(n)
}
