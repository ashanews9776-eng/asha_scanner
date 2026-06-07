package com.ahoura.asha_scanner_ip.core.prober

import com.ahoura.asha_scanner_ip.core.model.ProbeMode
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.net.Tls
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.net.Socket
import javax.net.ssl.SSLSocket
import kotlin.random.Random

/**
 * Phase-1 prober — a Kotlin port of SenPaiScanner's `internal/prober`.
 *
 * For each candidate IP it runs [ScanConfig.tries] handshake attempts at the
 * configured [ProbeMode] (TCP / TLS / HTTP), rotating the SNI to dodge DPI and
 * splitting the timeout budget across dial / handshake / request. Latency
 * samples for successful attempts are aggregated into a [ScanResult].
 */
class Prober(private val cfg: ScanConfig) {

    private val coloRegex = Regex("colo=([A-Za-z0-9]+)")

    /**
     * Probe [ip]:[port]. [preferredSni], when set, pins the SNI / Host header to
     * a specific name instead of rotating through [Tls.DEFAULT_SNIS] — used by
     * the open-site fallback so a domain-derived edge is approached exactly as a
     * real client of that site would (authentic SNI = harder for DPI to flag).
     */
    suspend fun probe(ip: String, port: Int, preferredSni: String? = null): ScanResult {
        val latencies = ArrayList<Long>(cfg.tries)
        var tlsOk = false
        var httpStatus = 0
        var colo = ""
        var attempts = 0

        // Timeout budget split (mirrors the Go prober): dial 1/4, TLS 1/2, HTTP 1/4.
        val total = cfg.timeoutMs.toInt().coerceAtLeast(800)
        val dialTo = (total / 4).coerceAtLeast(200)
        val tlsTo = (total / 2).coerceAtLeast(300)
        val httpTo = (total / 4).coerceAtLeast(200)

        repeat(cfg.tries) { attempt ->
            attempts++
            val sni = chooseSni(attempt, preferredSni)
            val start = System.nanoTime()
            var socket: Socket? = null
            var ssl: SSLSocket? = null
            try {
                socket = Tls.dial(ip, port, dialTo)
                when (cfg.mode) {
                    ProbeMode.TCP -> {
                        latencies.add(elapsedMs(start))
                    }
                    ProbeMode.TLS -> {
                        ssl = Tls.handshake(socket, ip, port, sni, emptyList(), insecure = true, tlsTo)
                        tlsOk = true
                        latencies.add(elapsedMs(start))
                    }
                    ProbeMode.HTTP -> {
                        ssl = Tls.handshake(socket, ip, port, sni, listOf("http/1.1"), insecure = true, tlsTo)
                        tlsOk = true
                        val resp = httpTrace(ssl, hostHeader = sni, timeoutMs = httpTo)
                        if (resp != null) {
                            httpStatus = resp.status
                            if (resp.colo.isNotEmpty()) colo = resp.colo
                            // Only count latency when we got a real Cloudflare response.
                            if (resp.status in 200..399 || resp.colo.isNotEmpty()) {
                                latencies.add(elapsedMs(start))
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // failed attempt — recorded as loss
            } finally {
                runCatching { ssl?.close() }
                runCatching { socket?.close() }
            }
            // jitter 10-50ms between attempts to avoid scan fingerprinting
            if (attempt < cfg.tries - 1) delay(10L + Random.nextLong(40))
        }

        val result = ScanResult(
            ip = ip,
            port = port,
            mode = cfg.mode,
            latenciesMs = latencies,
            attempts = attempts,
            tlsOk = tlsOk,
            httpStatus = httpStatus,
            colo = colo,
            timestamp = System.currentTimeMillis(),
        )
        return result.copy(healthy = isHealthy(result))
    }

    /** Health rule, ported from result.go's IsHealthy. */
    private fun isHealthy(r: ScanResult): Boolean {
        if (r.latenciesMs.isEmpty()) return false
        if (r.loss > 0.5) return false
        if (r.avgLatencyMs <= 0.0) return false
        return when (r.mode) {
            ProbeMode.TCP -> true
            ProbeMode.TLS -> r.tlsOk
            ProbeMode.HTTP ->
                r.tlsOk && (r.httpStatus in 200..399 || r.colo.isNotEmpty())
        }
    }

    private fun chooseSni(attempt: Int, preferredSni: String?): String {
        // Explicit user override wins everywhere; then a per-IP preferred SNI
        // (the fallback domain); otherwise rotate the default Cloudflare fronts.
        val override = cfg.sniOverride.trim()
        if (override.isNotEmpty()) return override
        if (!preferredSni.isNullOrBlank()) return preferredSni
        val list = Tls.DEFAULT_SNIS
        return list[attempt % list.size]
    }

    private data class TraceResp(val status: Int, val colo: String)

    private fun httpTrace(ssl: SSLSocket, hostHeader: String, timeoutMs: Int): TraceResp? {
        ssl.soTimeout = timeoutMs
        val host = hostHeader.ifBlank { "speed.cloudflare.com" }
        val req = buildString {
            append("GET /cdn-cgi/trace HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("User-Agent: Mozilla/5.0\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n\r\n")
        }
        ssl.outputStream.write(req.toByteArray(Charsets.US_ASCII))
        ssl.outputStream.flush()

        val reader: BufferedReader = ssl.inputStream.bufferedReader(Charsets.ISO_8859_1)
        val statusLine = reader.readLine() ?: return null
        val status = parseStatus(statusLine)
        var colo = ""
        val body = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line!!
            body.append(l).append('\n')
            val m = coloRegex.find(l)
            if (m != null) colo = m.groupValues[1]
            if (body.length > 8192) break
        }
        return TraceResp(status, colo)
    }

    private fun parseStatus(statusLine: String): Int {
        // "HTTP/1.1 200 OK"
        val parts = statusLine.split(' ')
        return if (parts.size >= 2) parts[1].toIntOrNull() ?: 0 else 0
    }

    private fun elapsedMs(startNanos: Long): Long =
        (System.nanoTime() - startNanos) / 1_000_000
}
