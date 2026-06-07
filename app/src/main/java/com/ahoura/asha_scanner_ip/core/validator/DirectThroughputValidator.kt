package com.ahoura.asha_scanner_ip.core.validator

import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.net.Tls
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Measures the real download throughput and latency of a candidate Cloudflare
 * edge by fetching from Cloudflare's own speed endpoint *through that specific
 * IP*. Because the candidate IP is a genuine Cloudflare front, this reflects the
 * quality you'll actually get when you point a VLESS/Trojan config at it.
 *
 * GET https://speed.cloudflare.com/__down?bytes=N  (SNI + Host = speed.cloudflare.com)
 */
class DirectThroughputValidator(
    private val downloadBytes: Int = 10_000_000,
    private val windowMs: Long = 5_000,
) : Validator {

    private val sni = "speed.cloudflare.com"

    override suspend fun validate(
        result: ScanResult,
        proxy: ProxyConfig?,
        cfg: ScanConfig,
    ): ScanResult {
        val connectTo = (cfg.timeoutMs / 2).toInt().coerceIn(1000, 8000)
        var socket: Socket? = null
        var ssl: SSLSocket? = null
        try {
            socket = Tls.dial(result.ip, result.port, connectTo)
            ssl = Tls.handshake(
                socket, result.ip, result.port, sni,
                alpn = listOf("http/1.1"), insecure = true, handshakeTimeoutMs = connectTo,
            )
            ssl.soTimeout = windowMs.toInt() + 2000

            val req = buildString {
                append("GET /__down?bytes=").append(downloadBytes).append(" HTTP/1.1\r\n")
                append("Host: ").append(sni).append("\r\n")
                append("User-Agent: Mozilla/5.0\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }
            val startNs = System.nanoTime()
            ssl.outputStream.write(req.toByteArray(Charsets.US_ASCII))
            ssl.outputStream.flush()

            val input = ssl.inputStream
            val buf = ByteArray(32 * 1024)
            var firstByteNs = 0L
            var headerDone = false
            var headerTail = 0          // tracks the \r\n\r\n boundary
            var bodyBytes = 0L
            val deadline = startNs + windowMs * 1_000_000

            loop@ while (System.nanoTime() < deadline) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                if (firstByteNs == 0L) firstByteNs = System.nanoTime()
                var i = 0
                if (!headerDone) {
                    // scan for end of headers \r\n\r\n
                    while (i < n) {
                        val b = buf[i].toInt()
                        headerTail = when {
                            headerTail == 0 && b == '\r'.code -> 1
                            headerTail == 1 && b == '\n'.code -> 2
                            headerTail == 2 && b == '\r'.code -> 3
                            headerTail == 3 && b == '\n'.code -> 4
                            else -> if (b == '\r'.code) 1 else 0
                        }
                        i++
                        if (headerTail == 4) { headerDone = true; break }
                    }
                    if (headerDone) bodyBytes += (n - i)
                } else {
                    bodyBytes += n
                }
            }

            // Steady-state throughput: measure from first byte to last, so the
            // connection/TTFB cost doesn't drag the rate down.
            val transferNs = (System.nanoTime() - (if (firstByteNs > 0) firstByteNs else startNs)).coerceAtLeast(1)
            val throughput = if (bodyBytes > 0) bodyBytes / (transferNs / 1e9) else 0.0
            val latency = if (firstByteNs > 0) (firstByteNs - startNs) / 1_000_000 else result.minLatencyMs

            // Merge the measured TTFB into the latency samples so sorting reflects it.
            val mergedLatencies = if (latency > 0)
                (result.latenciesMs + latency) else result.latenciesMs

            val ok = bodyBytes >= 8 * 1024 && throughput > 0
            return result.copy(
                latenciesMs = mergedLatencies,
                throughputBytesPerSec = throughput,
                speedTested = true,
                healthy = result.healthy && ok,
            )
        } catch (_: Throwable) {
            // Validation failed: keep Phase-1 result but mark speed test as run/empty.
            return result.copy(speedTested = true, throughputBytesPerSec = 0.0)
        } finally {
            runCatching { ssl?.close() }
            runCatching { socket?.close() }
        }
    }
}
