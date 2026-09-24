package com.ahoura.asha_scanner_ip.core.ipsrc

import java.io.InputStream

/**
 * Iran IPv4 range table parsed straight from the bundled (trimmed) geoip.dat —
 * no extra asset, no approximation. Used to auto-tag entries of the big
 * WhiteDNS resolver pool as ANTI_SANCTION so the category filter covers the
 * whole list, not just the curated shortlist.
 *
 * Wire format (protobuf, matching v2ray-core routercommon):
 *   GeoIPList { repeated GeoIP entry = 1; }        — file top level
 *   GeoIP    { string country_code = 1; repeated CIDR cidr = 2; }
 *   CIDR     { bytes ip = 1; uint32 prefix = 2; }
 */
class IrRangeTable private constructor(private val ranges: List<Range>) {

    data class Range(val base: Int, val prefix: Int) {
        private val mask: Int = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        fun contains(ip: Int): Boolean = (ip and mask) == (base and mask)
    }

    fun containsIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        var v = 0
        for (p in parts) {
            val n = p.toIntOrNull() ?: return false
            if (n !in 0..255) return false
            v = (v shl 8) or n
        }
        return containsInt(v)
    }

    fun containsInt(ip: Int): Boolean {
        for (r in ranges) {
            if (r.contains(ip)) return true
        }
        return false
    }

    val size: Int get() = ranges.size

    companion object {

        /** Parses the IR entry out of a geoip.dat stream; null if absent/corrupt. */
        fun load(input: InputStream): IrRangeTable? = try {
            val data = input.readBytes()
            input.close()
            load(data)
        } catch (_: Exception) {
            null
        }

        fun load(data: ByteArray): IrRangeTable? {
            val cidrs = ArrayList<Range>(3072)
            try {
                for (entry in iterMessages(data, topField = 1)) {
                    val code = readFieldString(entry, field = 1) ?: continue
                    if (!code.equals("IR", ignoreCase = true)) continue
                    for (cidr in iterMessages(entry, topField = 2)) {
                        val ipBytes = readFieldBytes(cidr, field = 1) ?: continue
                        val prefix = readFieldVarint(cidr, field = 2) ?: continue
                        if (ipBytes.size != 4) continue   // the pool is IPv4-only
                        var base = 0
                        for (b in ipBytes) base = (base shl 8) or (b.toInt() and 0xFF)
                        cidrs.add(Range(base and maskOf(prefix), prefix))
                    }
                    break   // single IR entry
                }
            } catch (_: Exception) {
                return null
            }
            return if (cidrs.isEmpty()) null else IrRangeTable(cidrs)
        }

        private fun maskOf(prefix: Int): Int = if (prefix <= 0) 0 else if (prefix >= 32) -1 else (-1 shl (32 - prefix))

        /** Iterates the payloads of one top-level repeated-message field. */
        private fun iterMessages(data: ByteArray, topField: Int): Sequence<ByteArray> = sequence {
            var i = 0
            while (i < data.size) {
                val tag = readVarint(data, i).also { i = it.second }.first
                val field = tag ushr 3
                val wire = tag and 7
                if (wire != 2) return@sequence   // unexpected structure — stop
                val len = readVarint(data, i).also { i = it.second }.first
                val payload = data.copyOfRange(i, i + len)
                i += len
                if (field == topField) yield(payload)
            }
        }

        private fun readFieldString(msg: ByteArray, field: Int): String? =
            readFieldBytes(msg, field)?.let { String(it, Charsets.UTF_8) }

        private fun readFieldBytes(msg: ByteArray, field: Int): ByteArray? {
            var i = 0
            while (i < msg.size) {
                val tag = readVarint(msg, i).also { i = it.second }.first
                val f = tag ushr 3
                val wire = tag and 7
                if (wire == 0) {
                    i = readVarint(msg, i).second
                    continue
                }
                if (wire != 2) return null
                val len = readVarint(msg, i).also { i = it.second }.first
                if (f == field) return msg.copyOfRange(i, i + len)
                i += len
            }
            return null
        }

        private fun readFieldVarint(msg: ByteArray, field: Int): Int? {
            var i = 0
            while (i < msg.size) {
                val tag = readVarint(msg, i).also { i = it.second }.first
                val f = tag ushr 3
                val wire = tag and 7
                if (wire == 0) {
                    if (f == field) return readVarint(msg, i).first
                    i = readVarint(msg, i).second
                    continue
                }
                if (wire != 2) return null
                val len = readVarint(msg, i).also { i = it.second }.first
                i += len
            }
            return null
        }

        private fun readVarint(buf: ByteArray, start: Int): Pair<Int, Int> {
            var result = 0
            var shift = 0
            var i = start
            while (true) {
                val b = buf[i].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                i++
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result to i
        }
    }
}
