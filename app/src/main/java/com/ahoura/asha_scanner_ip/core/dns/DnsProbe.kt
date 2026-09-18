package com.ahoura.asha_scanner_ip.core.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Hand-rolled DNS A-record probing (RFC 1035 wire format) — the engine behind
 * the DNS Tuner. No library dependency: we craft the query, fire it over UDP
 * to a resolver, and time the answer, so resolver reachability and latency are
 * measured exactly as the device will experience them.
 */
object DnsProbe {

    enum class ResolverCategory {
        ANTI_SANCTION,
        GLOBAL,
        CUSTOM,
    }

    /** A resolver entry from the bundled list or user additions. */
    data class ResolverDef(
        val name: String,
        val ip: String,
        val category: ResolverCategory = ResolverCategory.GLOBAL,
    )

    /** Outcome of probing one resolver: best successful round-trip, if any. */
    data class ProbeResult(
        val ip: String,
        val name: String,
        val category: ResolverCategory = ResolverCategory.GLOBAL,
        val latencyMs: Long? = null, // null = no valid answer within any try
        val tries: Int = 0,
        val passRate: Double = 0.0,
        val jitterMs: Long = 0L,
    )

    /** Validates an IPv4 or IPv6 address string. */
    fun isValidIp(ip: String): Boolean {
        val trimmed = ip.trim()
        val parts = trimmed.split('.')
        if (parts.size == 4) {
            return parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
        }
        return trimmed.contains(':') && runCatching { InetAddress.getByName(trimmed) }.isSuccess
    }

    /** Parses a line formatted as `Category|Name|IP`, `Name|IP`, or bare `IP`. */
    fun parseResolverLine(line: String): ResolverDef? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split('|').map { it.trim() }
        return when (parts.size) {
            3 -> {
                val cat = when (parts[0].uppercase()) {
                    "ANTI_SANCTION", "IR", "SANCTION" -> ResolverCategory.ANTI_SANCTION
                    "CUSTOM" -> ResolverCategory.CUSTOM
                    else -> ResolverCategory.GLOBAL
                }
                if (isValidIp(parts[2])) ResolverDef(name = parts[1], ip = parts[2], category = cat) else null
            }
            2 -> {
                if (isValidIp(parts[1])) ResolverDef(name = parts[0], ip = parts[1], category = ResolverCategory.GLOBAL) else null
            }
            1 -> {
                val ip = parts[0]
                if (isValidIp(ip)) ResolverDef(name = ip, ip = ip, category = ResolverCategory.CUSTOM) else null
            }
            else -> null
        }
    }

    /**
     * Build a single-question A query. Layout:
     * `id[2] | flags=0x0100 (RD) | qdcount=1 | an/ns/arcount=0 | QNAME | type=A | class=IN`
     */
    fun buildQuery(id: Int, domain: String): ByteArray {
        val labels = domain.trim('.').split('.').filter { it.isNotEmpty() }
        val out = ByteArray(12 + labels.sumOf { it.length + 1 } + 1 + 4)
        var i = 0
        out[i++] = (id ushr 8).toByte()
        out[i++] = id.toByte()
        out[i++] = 0x01                    // flags hi: RD=1
        out[i++] = 0x00                    // flags lo
        out[i++] = 0x00; out[i++] = 0x01   // qdcount = 1
        out[i++] = 0x00; out[i++] = 0x00   // ancount = 0
        out[i++] = 0x00; out[i++] = 0x00   // nscount = 0
        out[i++] = 0x00; out[i++] = 0x00   // arcount = 0
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out[i++] = bytes.size.toByte()
            System.arraycopy(bytes, 0, out, i, bytes.size)
            i += bytes.size
        }
        out[i++] = 0x00                    // root label terminates QNAME
        out[i++] = 0x00; out[i++] = 0x01   // qtype = A
        out[i++] = 0x00; out[i] = 0x01     // qclass = IN
        return out
    }

    /**
     * Validate an answer packet: matching id, QR set, RCODE 0, and at least one
     * A record present. Enough rigor to reject garbage/redirects, tolerant of
     * compressed or literal owner names in the answer section.
     */
    fun isAnswer(packet: ByteArray, expectedId: Int): Boolean {
        if (packet.size < 12) return false
        val id = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        if (id != expectedId) return false
        val flags = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (flags and 0x8000 == 0) return false          // QR: not a response
        if (flags and 0x000F != 0) return false          // RCODE != NOERROR
        val anCount = ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF)
        if (anCount <= 0) return false
        // Skip the question section, then scan answer records for type A.
        var i = 12
        val qdCount = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        repeat(qdCount) {
            i = skipName(packet, i)
            i += 4                                        // qtype + qclass
        }
        repeat(anCount) {
            i = skipName(packet, i)
            if (i + 10 > packet.size) return false
            val type = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            if (type == 1) return true                    // A record found
            i += 10                                       // type + class + ttl[4] + rdlength[2]
            val rdLength = ((packet[i - 2].toInt() and 0xFF) shl 8) or (packet[i - 1].toInt() and 0xFF)
            i += rdLength
        }
        return false
    }

    /** Advance past a (possibly compressed) name field; returns the index after it. */
    private fun skipName(p: ByteArray, start: Int): Int {
        var i = start
        while (i < p.size) {
            val len = p[i].toInt() and 0xFF
            if (len == 0) return i + 1
            if (len and 0xC0 == 0xC0) return i + 2        // compression pointer
            i += 1 + len
        }
        return i
    }

    /**
     * Probe [ip]:53 (or [port]) with [tries] A-lookups of [domain]; the best
     * successful round-trip wins. A valid answer is the pass criterion — a
     * resolver that answers fast with garbage is not a resolver.
     */
    suspend fun probe(
        resolver: ResolverDef,
        domain: String,
        port: Int = 53,
        tries: Int = 2,
        timeoutMs: Int = 2_000,
    ): ProbeResult = withContext(Dispatchers.IO) {
        var best: Long? = null
        val samples = mutableListOf<Long>()
        var successCount = 0
        for (attempt in 0 until tries) {
            val id = Random.nextInt(1, 65536)
            val query = buildQuery(id, domain)
            val addr = runCatching { InetAddress.getByName(resolver.ip) }.getOrNull() ?: break
            val recv = DatagramPacket(ByteArray(512), 512)
            val startedNs = System.nanoTime()
            DatagramSocket().use { socket ->
                runCatching {
                    socket.soTimeout = timeoutMs
                    socket.send(DatagramPacket(query, query.size, addr, port))
                    while (true) {
                        socket.receive(recv)
                        if (isAnswer(recv.data.copyOf(recv.length), id)) {
                            val ms = (System.nanoTime() - startedNs) / 1_000_000
                            samples.add(ms)
                            successCount++
                            if (best == null || ms < best!!) best = ms
                            break
                        }
                        // stray/late packet for another id — keep listening
                    }
                }
            }
        }
        val passRate = if (tries > 0) successCount.toDouble() / tries else 0.0
        val jitter = if (samples.size > 1) {
            (samples.maxOrNull() ?: 0L) - (samples.minOrNull() ?: 0L)
        } else 0L
        ProbeResult(
            ip = resolver.ip,
            name = resolver.name,
            category = resolver.category,
            latencyMs = best,
            tries = tries,
            passRate = passRate,
            jitterMs = jitter,
        )
    }

    /**
     * Probe a list in parallel (capped so hundreds of resolvers don't spawn
     * hundreds of sockets at once) and stream each result through [onResult]
     * as it lands, so the UI fills in live.
     */
    suspend fun probeAll(
        resolvers: List<ResolverDef>,
        domain: String,
        concurrency: Int = 8,
        tries: Int = 2,
        timeoutMs: Int = 2_000,
        onResult: suspend (ProbeResult) -> Unit,
    ): Unit = coroutineScope {
        val safeConcurrency = concurrency.coerceIn(1, 32).coerceAtMost(resolvers.size.coerceAtLeast(1))
        val lanes = Array(safeConcurrency) { mutableListOf<ResolverDef>() }
        resolvers.forEachIndexed { idx, r -> lanes[idx % safeConcurrency].add(r) }
        lanes.filter { it.isNotEmpty() }.map { lane ->
            async(Dispatchers.IO) {
                for (r in lane) {
                    ensureActive()
                    val res = probe(r, domain, tries = tries, timeoutMs = timeoutMs)
                    ensureActive()
                    onResult(res)
                }
            }
        }.awaitAll()
    }
}
