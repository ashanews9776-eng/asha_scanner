package com.ahoura.asha_scanner_ip.core.ipsrc

import java.net.Inet6Address
import java.net.InetAddress
import kotlin.random.Random

/**
 * Generates random candidate IPs from Cloudflare's published CIDR ranges.
 *
 * Kotlin port of SenPaiScanner's `internal/ipsrc`. IPv4 ranges are treated as
 * 32-bit integers (base | random-host-offset); IPv6 ranges randomise the host
 * portion byte-by-byte. The [stream] sequence de-duplicates emitted addresses.
 */
class IpSource private constructor(
    private val v4: List<V4Net>,
    private val v6: List<V6Net>,
    private val rng: Random,
) {
    data class V4Net(val base: Int, val mask: Int)             // 32-bit
    data class V6Net(val base: ByteArray, val mask: ByteArray) // 16 bytes each

    private fun randomV4(n: V4Net): String {
        val size = n.mask.inv()                       // host bits as mask
        val offset = rng.nextInt().and(size)
        val ip = n.base or offset
        return "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}." +
            "${(ip ushr 8) and 0xFF}.${ip and 0xFF}"
    }

    private fun randomV6(n: V6Net): String {
        val ip = ByteArray(16)
        for (i in 0 until 16) {
            val host = rng.nextInt(256) and n.mask[i].toInt().inv()
            ip[i] = (n.base[i].toInt() or host).toByte()
        }
        // Compress to canonical IPv6 text (e.g. 2606:4700::1).
        return (InetAddress.getByAddress(ip) as Inet6Address).hostAddress ?: bytesToHexV6(ip)
    }

    private fun randomOne(): String {
        val totalV4 = v4.size
        val total = totalV4 + v6.size
        val idx = rng.nextInt(total)
        return if (idx < totalV4) randomV4(v4[idx]) else randomV6(v6[idx - totalV4])
    }

    /**
     * Emits up to [count] unique random IPs (count <= 0 means unbounded).
     * Duplicates are skipped. Backed by a lazy sequence so the engine can pull
     * on demand and stop early on cancellation.
     */
    fun stream(count: Int): Sequence<String> = sequence {
        val seen = HashSet<String>(if (count > 0) count * 2 else 1024)
        var sent = 0
        var guard = 0
        val maxGuard = if (count > 0) count * 50L else Long.MAX_VALUE
        while (count <= 0 || sent < count) {
            if (guard++ > maxGuard) break // address space exhausted for this size
            val ip = randomOne()
            if (seen.add(ip)) {
                sent++
                yield(ip)
            }
        }
    }

    companion object {
        /**
         * Build a source. [extra] accepts additional CIDR strings (v4 or v6),
         * sorted automatically into the right family.
         */
        fun build(
            useV4: Boolean,
            useV6: Boolean,
            extra: List<String> = emptyList(),
            seed: Long? = null,
            v4Ranges: List<String> = CloudflareRanges.V4,
            v6Ranges: List<String> = CloudflareRanges.V6,
        ): IpSource {
            val v4 = ArrayList<V4Net>()
            val v6 = ArrayList<V6Net>()
            // Range lists may come from a bundled asset with thousands of lines;
            // skip any malformed line rather than failing the whole scan.
            if (useV4) v4Ranges.forEach { runCatching { parseInto(it.trim(), v4, v6) } }
            if (useV6) v6Ranges.forEach { runCatching { parseInto(it.trim(), v4, v6) } }
            extra.forEach { if (it.isNotBlank()) runCatching { parseInto(it.trim(), v4, v6) } }
            require(v4.isNotEmpty() || v6.isNotEmpty()) {
                "no IP ranges available (enable IPv4 and/or IPv6)"
            }
            val rng = if (seed != null) Random(seed) else Random.Default
            return IpSource(v4, v6, rng)
        }

        private fun parseInto(cidr: String, v4: MutableList<V4Net>, v6: MutableList<V6Net>) {
            val slash = cidr.indexOf('/')
            require(slash > 0) { "invalid CIDR: $cidr" }
            val host = cidr.substring(0, slash)
            val prefix = cidr.substring(slash + 1).toInt()
            val addr = InetAddress.getByName(host)
            val bytes = addr.address
            if (bytes.size == 4) {
                var base = 0
                for (b in bytes) base = (base shl 8) or (b.toInt() and 0xFF)
                val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
                v4.add(V4Net(base and mask, mask))
            } else {
                val mask = ByteArray(16)
                var rem = prefix
                for (i in 0 until 16) {
                    mask[i] = when {
                        rem >= 8 -> 0xFF.toByte()
                        rem <= 0 -> 0
                        else -> (0xFF shl (8 - rem)).toByte()
                    }
                    rem -= 8
                }
                val maskedBase = ByteArray(16) { (bytes[it].toInt() and mask[it].toInt()).toByte() }
                v6.add(V6Net(maskedBase, mask))
            }
        }

        private fun bytesToHexV6(b: ByteArray): String =
            (0 until 16 step 2).joinToString(":") {
                Integer.toHexString(((b[it].toInt() and 0xFF) shl 8) or (b[it + 1].toInt() and 0xFF))
            }
    }
}
