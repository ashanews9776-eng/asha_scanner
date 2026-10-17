package com.ahoura.asha_scanner_ip.core.ipsrc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.InetAddress
import kotlin.random.Random

data class TargetPreview(
    val count: Int = 0,
    val domains: Int = 0,
    val skipped: List<String> = emptyList(),
    val ips: List<String> = emptyList(),
)

/**
 * Parses free text containing target IPs, CIDRs, IP ranges, and domain names.
 * Kotlin port of SenPaiScanner's `internal/scanjob/targets.go`.
 */
object TargetParser {

    private const val CIDR_SAMPLE = 256
    private const val RANGE_MAX = 4096

    suspend fun parse(text: String, resolveDomains: Boolean = true): TargetPreview = coroutineScope {
        if (text.isBlank()) return@coroutineScope TargetPreview()

        val rawTokens = extractTokens(text)
        val ips = LinkedHashSet<String>()
        val skipped = mutableListOf<String>()
        val domainsToResolve = mutableListOf<String>()

        for (tok in rawTokens) {
            val host = stripPort(tok)
            when {
                isIpLiteral(host) -> {
                    ips.add(host)
                }
                host.contains("/") -> {
                    val cidrIps = fromCidr(host)
                    if (cidrIps != null) {
                        ips.addAll(cidrIps)
                    } else {
                        skipped.add("$tok (invalid CIDR format)")
                    }
                }
                host.contains("-") && isRange(host) -> {
                    val rangeIps = fromRange(host)
                    if (rangeIps != null) {
                        ips.addAll(rangeIps)
                    } else {
                        skipped.add("$tok (invalid range format)")
                    }
                }
                looksLikeDomain(host) -> {
                    domainsToResolve.add(host.lowercase())
                }
                else -> {
                    skipped.add("$tok (not an IP, CIDR, range or domain)")
                }
            }
        }

        var resolvedDomainCount = 0
        if (resolveDomains && domainsToResolve.isNotEmpty()) {
            val uniqueDomains = domainsToResolve.distinct()
            val resolveJobs = uniqueDomains.map { domain ->
                async(Dispatchers.IO) {
                    val res = runCatching {
                        InetAddress.getAllByName(domain).mapNotNull { it.hostAddress }
                    }.getOrNull()
                    domain to res
                }
            }
            val results = resolveJobs.awaitAll()
            for ((domain, resolvedIps) in results) {
                if (!resolvedIps.isNullOrEmpty()) {
                    resolvedDomainCount++
                    ips.addAll(resolvedIps)
                } else {
                    skipped.add("$domain (could not be resolved)")
                }
            }
        }

        val shuffled = ips.toMutableList()
        shuffled.shuffle()

        TargetPreview(
            count = shuffled.size,
            domains = resolvedDomainCount,
            skipped = skipped,
            ips = shuffled,
        )
    }

    private fun extractTokens(text: String): List<String> {
        val out = mutableListOf<String>()
        text.lineSequence().forEach { line ->
            val clean = line.substringBefore('#').trim()
            if (clean.isNotEmpty()) {
                clean.split(Regex("[,;\\s]+")).forEach { token ->
                    val t = token.trim()
                    if (t.isNotEmpty()) out.add(t)
                }
            }
        }
        return out
    }

    private fun stripPort(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("[") && s.contains("]")) {
            val close = s.indexOf(']')
            return s.substring(1, close)
        }
        if (s.count { it == ':' } == 1) {
            return s.substringBefore(':')
        }
        return s
    }

    private fun isIpLiteral(s: String): Boolean = runCatching {
        val addr = InetAddress.getByName(s)
        addr.hostAddress == s || s.matches(Regex("^[0-9.]+$")) || s.matches(Regex("^[0-9a-fA-F:]+$"))
    }.getOrDefault(false)

    private fun fromCidr(cidr: String): List<String>? {
        val parts = cidr.split('/')
        if (parts.size != 2) return null
        val baseIp = parts[0].trim()
        val prefix = parts[1].trim().toIntOrNull() ?: return null

        if (!baseIp.contains(":")) {
            // IPv4
            if (prefix !in 0..32) return null
            val baseInt = runCatching { ipToInt(baseIp) }.getOrNull() ?: return null
            val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
            val net = baseInt and mask
            val totalHosts = (1L shl (32 - prefix)).coerceAtLeast(1)

            return if (totalHosts <= CIDR_SAMPLE) {
                (0 until totalHosts).map { intToIp(net + it) }
            } else {
                val rnd = Random.Default
                (0 until CIDR_SAMPLE).map {
                    val offset = rnd.nextLong(totalHosts)
                    intToIp(net + offset)
                }.distinct()
            }
        }
        // IPv6 literal base
        return if (isIpLiteral(baseIp)) listOf(baseIp) else null
    }

    private fun isRange(s: String): Boolean {
        val parts = s.split('-')
        if (parts.size != 2) return false
        val first = parts[0].trim()
        return first.matches(Regex("^[0-9.]+$"))
    }

    private fun fromRange(range: String): List<String>? {
        val parts = range.split('-')
        if (parts.size != 2) return null
        val startStr = parts[0].trim()
        val endPart = parts[1].trim()

        val startInt = runCatching { ipToInt(startStr) }.getOrNull() ?: return null
        val endInt = if (endPart.contains(".")) {
            runCatching { ipToInt(endPart) }.getOrNull() ?: return null
        } else {
            // Shorthand e.g. 192.168.1.1-50
            val lastOctet = endPart.toLongOrNull() ?: return null
            val startOctets = startStr.split('.')
            if (startOctets.size != 4) return null
            val base = (startOctets[0].toLong() shl 24) or (startOctets[1].toLong() shl 16) or (startOctets[2].toLong() shl 8)
            base or (lastOctet and 0xFF)
        }

        val low = minOf(startInt, endInt)
        val high = maxOf(startInt, endInt)
        val count = (high - low + 1).coerceAtMost(RANGE_MAX.toLong())

        return (0 until count).map { intToIp(low + it) }
    }

    private fun looksLikeDomain(s: String): Boolean {
        if (s.contains('/') || s.contains(':') || s.contains(' ')) return false
        return s.contains('.') && s.matches(Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"))
    }

    private fun ipToInt(ip: String): Long {
        val parts = ip.split('.').map { it.toLong() }
        if (parts.size != 4) throw IllegalArgumentException("Not an IPv4: $ip")
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }

    private fun intToIp(value: Long): String {
        val a = (value shr 24) and 0xFF
        val b = (value shr 16) and 0xFF
        val c = (value shr 8) and 0xFF
        val d = value and 0xFF
        return "$a.$b.$c.$d"
    }
}
