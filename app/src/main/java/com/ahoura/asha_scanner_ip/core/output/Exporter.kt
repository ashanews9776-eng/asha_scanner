package com.ahoura.asha_scanner_ip.core.output

import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult

/** Formats results for clipboard / file export. Port of `internal/output`. */
object Exporter {

    /** Plain list of `ip:port` endpoints, one per line — paste-ready. */
    fun endpoints(results: List<ScanResult>): String =
        results.joinToString("\n") { it.endpoint }

    /** Ready-to-use config links (clean IP swapped into the user's proxy link). */
    fun configs(results: List<ScanResult>, proxy: ProxyConfig?): String {
        if (proxy == null) return endpoints(results)
        return results.joinToString("\n") { ConfigLinkBuilder.withAddress(proxy, it.ip) }
    }

    /** CSV with full metrics. */
    fun csv(results: List<ScanResult>): String = buildString {
        appendLine("ip,loss_pct,avg_ms,min_ms,max_ms,jitter_ms,download_kbps,speed_tested,colo,tls_ok,http_status")
        results.forEach { r ->
            val max = r.latenciesMs.maxOrNull() ?: 0L
            val dl = (r.throughputBytesPerSec / 1024).toInt()
            appendLine(
                "${r.ip},${(r.loss * 100).toInt()},${r.avgLatencyMs.toInt()},${r.minLatencyMs}," +
                    "$max,${r.jitterMs},$dl,${r.speedTested},${r.colo},${r.tlsOk},${r.httpStatus}"
            )
        }
    }

    /** JSON array of result objects (built manually — no extra deps). */
    fun json(results: List<ScanResult>): String = buildString {
        append("[\n")
        results.forEachIndexed { i, r ->
            val dl = (r.throughputBytesPerSec / 1024).toInt()
            append("  {")
            append("\"ip\":\"${r.ip}\",")
            append("\"loss_pct\":${(r.loss * 100).toInt()},")
            append("\"avg_ms\":${r.avgLatencyMs.toInt()},")
            append("\"min_ms\":${r.minLatencyMs},")
            append("\"jitter_ms\":${r.jitterMs},")
            append("\"download_kbps\":$dl,")
            append("\"speed_tested\":${r.speedTested},")
            append("\"colo\":\"${r.colo}\",")
            append("\"tls_ok\":${r.tlsOk},")
            append("\"http_status\":${r.httpStatus}")
            append("}")
            if (i < results.lastIndex) append(",")
            append("\n")
        }
        append("]")
    }

    /** Tab-separated quick TXT. */
    fun txt(results: List<ScanResult>): String = results.joinToString("\n") { r ->
        val dl = (r.throughputBytesPerSec / 1024).toInt()
        "${r.ip}\tloss=${(r.loss * 100).toInt()}%\tavg=${r.avgLatencyMs.toInt()}ms\t" +
            "jitter=${r.jitterMs}ms\tdl=${dl}KB/s\tcolo=${r.colo}"
    }

    /** Human-readable report with latency / speed / colo per IP. */
    fun report(results: List<ScanResult>): String = buildString {
        appendLine("# Asha Scanner — clean Cloudflare IPs")
        appendLine("# endpoint, avg_latency_ms, speed_mbps, colo, loss%")
        results.forEach { r ->
            appendLine(
                "%s, %.0f, %.2f, %s, %.0f".format(
                    r.endpoint,
                    r.avgLatencyMs,
                    r.throughputMbps,
                    r.colo.ifBlank { "-" },
                    r.loss * 100,
                )
            )
        }
    }
}
