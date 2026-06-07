package com.ahoura.asha_scanner_ip.core.validator

import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.net.Tls
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import javax.net.ssl.SSLSocket
import kotlin.random.Random

/**
 * Phase-2 validator that measures throughput **through the user's actual proxy
 * config**, not just against the raw Cloudflare edge.
 *
 * For each candidate edge IP it:
 *   1. dials the IP and completes the outer TLS handshake with the config's SNI,
 *   2. speaks the real VLESS / Trojan handshake to the origin behind Cloudflare
 *      (over a plain-TCP or WebSocket transport),
 *   3. asks that proxy to fetch `http://speed.cloudflare.com/__down?bytes=N`, and
 *   4. times the download — so latency + speed reflect the genuine end-to-end
 *      tunnel a v2rayNG/NekoBox client would get when pointed at this IP.
 *
 * This is a pure-Kotlin client (no native xray-core). It covers the common
 * VLESS/Trojan-over-(TCP|WS)+TLS configs. Transports it can't speak natively
 * (Reality, gRPC, xhttp) — or any tunnel failure — gracefully fall back to
 * [DirectThroughputValidator], which still yields a comparable edge speed.
 */
class TunnelValidator(
    private val downloadBytes: Int = 10_000_000,
    private val windowMs: Long = 5_000,
    private val fallback: Validator = DirectThroughputValidator(),
) : Validator {

    private val measureHost = "speed.cloudflare.com"
    private val measurePort = 80

    override suspend fun validate(
        result: ScanResult,
        proxy: ProxyConfig?,
        cfg: ScanConfig,
    ): ScanResult {
        if (proxy == null || !canTunnel(proxy)) return fallback.validate(result, proxy, cfg)
        return try {
            measureThroughTunnel(result, proxy, cfg)
        } catch (_: Throwable) {
            // Tunnel couldn't be established (server quirk, unsupported flow, …):
            // fall back to a direct edge measurement so the IP still gets a score.
            fallback.validate(result, proxy, cfg)
        }
    }

    /** We can speak VLESS/Trojan over plain-TCP or WebSocket TLS in pure Kotlin. */
    private fun canTunnel(p: ProxyConfig): Boolean {
        val protoOk = p.protocol == Protocol.VLESS || p.protocol == Protocol.TROJAN
        val netOk = p.network == "tcp" || p.network == "ws"
        val secOk = !p.security.equals("reality", ignoreCase = true)
        return protoOk && netOk && secOk
    }

    private fun measureThroughTunnel(
        result: ScanResult,
        proxy: ProxyConfig,
        cfg: ScanConfig,
    ): ScanResult {
        val connectTo = (cfg.timeoutMs / 2).toInt().coerceIn(1500, 8000)
        val sni = proxy.effectiveSni()
        var socket: Socket? = null
        var ssl: SSLSocket? = null
        try {
            socket = Tls.dial(result.ip, result.port, connectTo)
            val alpn = if (proxy.network == "ws") listOf("http/1.1")
            else proxy.alpn.ifEmpty { listOf("http/1.1") }
            ssl = Tls.handshake(
                socket, result.ip, result.port, sni,
                alpn = alpn, insecure = true, handshakeTimeoutMs = connectTo,
            )
            ssl.soTimeout = windowMs.toInt() + 3000

            // Wrap the transport: raw TLS stream, or WebSocket frames over TLS.
            val tunnel: Tunnel = if (proxy.network == "ws") {
                WsTunnel.open(ssl, path = proxy.path.ifBlank { "/" }, host = proxy.hostHeader.ifBlank { sni })
            } else {
                RawTunnel(ssl)
            }

            // Proxy handshake header + inner HTTP request, sent as one payload.
            val header = when (proxy.protocol) {
                Protocol.VLESS -> ProxyHandshake.vlessHeader(proxy.uuid, measureHost, measurePort)
                Protocol.TROJAN -> ProxyHandshake.trojanHeader(proxy.password, measureHost, measurePort)
            }
            val httpReq = buildString {
                append("GET /__down?bytes=").append(downloadBytes).append(" HTTP/1.1\r\n")
                append("Host: ").append(measureHost).append("\r\n")
                append("User-Agent: Mozilla/5.0\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.US_ASCII)

            val startNs = System.nanoTime()
            tunnel.send(header + httpReq)
            tunnel.send(ByteArray(0)) // flush

            val input = tunnel.input
            // VLESS prepends a small response header (version + addons) before relayed data.
            if (proxy.protocol == Protocol.VLESS) consumeVlessResponseHeader(input)

            // ---- Read & time the relayed HTTP response ----
            val buf = ByteArray(32 * 1024)
            var firstByteNs = 0L
            var headerDone = false
            var headerTail = 0
            var bodyBytes = 0L
            val deadline = startNs + windowMs * 1_000_000

            while (System.nanoTime() < deadline) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                if (firstByteNs == 0L) firstByteNs = System.nanoTime()
                var i = 0
                if (!headerDone) {
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

            if (bodyBytes < 8 * 1024) throw IOException("tunnel returned too little data")

            val transferNs = (System.nanoTime() - (if (firstByteNs > 0) firstByteNs else startNs)).coerceAtLeast(1)
            val throughput = bodyBytes / (transferNs / 1e9)
            val latency = if (firstByteNs > 0) (firstByteNs - startNs) / 1_000_000 else result.minLatencyMs
            val mergedLatencies = if (latency > 0) (result.latenciesMs + latency) else result.latenciesMs

            return result.copy(
                latenciesMs = mergedLatencies,
                throughputBytesPerSec = throughput,
                speedTested = true,
                healthy = result.healthy && throughput > 0,
            )
        } finally {
            runCatching { ssl?.close() }
            runCatching { socket?.close() }
        }
    }

    /** Reads and discards the VLESS response header: ver(1) + addonLen(1) + addons. */
    private fun consumeVlessResponseHeader(input: InputStream) {
        readFully(input, 1)                       // version
        val n = readFully(input, 1)[0].toInt() and 0xFF
        if (n > 0) readFully(input, n)            // addons
    }

    // ── Transports ───────────────────────────────────────────────────────────

    /** A bidirectional inner stream over which the proxy handshake + data flow. */
    private interface Tunnel {
        val input: InputStream
        fun send(bytes: ByteArray)
    }

    /** Plain TLS stream (network=tcp): write straight through. */
    private class RawTunnel(ssl: SSLSocket) : Tunnel {
        private val out: OutputStream = ssl.outputStream
        override val input: InputStream = ssl.inputStream
        override fun send(bytes: ByteArray) {
            if (bytes.isNotEmpty()) out.write(bytes)
            out.flush()
        }
    }

    /**
     * WebSocket transport (network=ws): completes the HTTP Upgrade, then frames
     * client->server data as masked binary frames and unwraps server->client
     * frames back into a plain byte stream.
     */
    private class WsTunnel private constructor(
        private val out: OutputStream,
        rawIn: InputStream,
    ) : Tunnel {
        override val input: InputStream = WsInputStream(rawIn)

        override fun send(bytes: ByteArray) {
            if (bytes.isEmpty()) { out.flush(); return }
            val mask = ByteArray(4) { Random.nextInt(256).toByte() }
            out.write(ProxyHandshake.wsBinaryFrame(bytes, mask))
            out.flush()
        }

        companion object {
            fun open(ssl: SSLSocket, path: String, host: String): WsTunnel {
                val key = java.util.Base64.getEncoder()
                    .encodeToString(ByteArray(16) { Random.nextInt(256).toByte() })
                val req = buildString {
                    append("GET ").append(path).append(" HTTP/1.1\r\n")
                    append("Host: ").append(host).append("\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: ").append(key).append("\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    append("User-Agent: Mozilla/5.0\r\n\r\n")
                }
                ssl.outputStream.write(req.toByteArray(Charsets.US_ASCII))
                ssl.outputStream.flush()

                // Read the upgrade response header (raw, byte-by-byte to \r\n\r\n,
                // so we don't consume any frame bytes that follow).
                val rawIn = ssl.inputStream
                val statusLine = StringBuilder()
                var tail = 0
                while (true) {
                    val b = rawIn.read()
                    if (b < 0) throw IOException("ws upgrade: connection closed")
                    statusLine.append(b.toChar())
                    tail = when {
                        tail == 0 && b == '\r'.code -> 1
                        tail == 1 && b == '\n'.code -> 2
                        tail == 2 && b == '\r'.code -> 3
                        tail == 3 && b == '\n'.code -> 4
                        else -> if (b == '\r'.code) 1 else 0
                    }
                    if (tail == 4) break
                }
                if (!statusLine.contains(" 101")) throw IOException("ws upgrade rejected: ${statusLine.take(40)}")
                return WsTunnel(ssl.outputStream, rawIn)
            }
        }
    }

    /** Unwraps inbound WebSocket frames (server frames are unmasked) into bytes. */
    private class WsInputStream(private val src: InputStream) : InputStream() {
        private var remaining = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (remaining == 0L) {
                if (!nextFrame()) return -1
            }
            val want = minOf(len.toLong(), remaining).toInt()
            val n = src.read(b, off, want)
            if (n < 0) return -1
            remaining -= n
            return n
        }

        /** Reads the next frame header; returns false on a close frame / EOF. */
        private fun nextFrame(): Boolean {
            val b0 = src.read()
            if (b0 < 0) return false
            val b1 = src.read()
            if (b1 < 0) return false
            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()
            when (len.toInt()) {
                126 -> len = ((readU8() shl 8) or readU8()).toLong()
                127 -> { len = 0; repeat(8) { len = (len shl 8) or readU8().toLong() } }
            }
            if (masked) repeat(4) { readU8() } // servers shouldn't mask; tolerate it
            if (opcode == 0x8) return false     // close
            if (opcode == 0x9 || opcode == 0xA) { // ping/pong: skip payload, get next
                skipFully(len)
                return nextFrame()
            }
            remaining = len
            // A zero-length data frame: advance to the next one.
            if (remaining == 0L) return nextFrame()
            return true
        }

        private fun readU8(): Int {
            val v = src.read()
            if (v < 0) throw IOException("ws: unexpected EOF in frame header")
            return v and 0xFF
        }

        private fun skipFully(count: Long) {
            var left = count
            while (left > 0) {
                val s = src.skip(left)
                if (s <= 0) { if (src.read() < 0) return else left-- } else left -= s
            }
        }
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(out, read, count - read)
            if (n < 0) throw IOException("unexpected EOF reading $count bytes")
            read += n
        }
        return out
    }
}
