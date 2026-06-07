package com.ahoura.asha_scanner_ip.core.ipsrc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Resolves a list of Cloudflare-fronted domains to their edge IPs.
 *
 * This backs the *open-site fallback*: when a normal range scan finds zero
 * reachable IPs (e.g. the ISP null-routes the usual Cloudflare blocks), we
 * instead take the domains of popular sites that are *known to load* through
 * Cloudflare and resolve them to the exact anycast IPs serving those sites —
 * addresses the network demonstrably lets through. Those IPs are then fed back
 * into the Phase-1 prober as candidates.
 *
 * Resolution unions two independent paths so a poisoned or unreachable one can't
 * starve the result:
 *  - the platform resolver ([InetAddress]) — uses the active network's DNS
 *  - DNS-over-HTTPS (Google + Cloudflare JSON) — sidesteps local DNS tampering
 */
object DomainResolver {

    /** JSON DoH endpoints, queried best-effort and unioned with system DNS. */
    private val DOH_PROVIDERS = listOf(
        "https://dns.google/resolve",
        "https://cloudflare-dns.com/dns-query",
    )

    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28

    /** An edge IP together with the Cloudflare-fronted domain it was resolved from. */
    data class ResolvedHost(val ip: String, val domain: String)

    /**
     * Resolve [domains] to de-duplicated [ResolvedHost]s, each carrying the
     * domain it came from so the prober can present that domain as the SNI.
     *
     * Results are round-robined across domains so that truncating to [limit]
     * still spans many sites rather than draining one domain's addresses first.
     * When an anycast IP is served by several domains, the first one wins.
     *
     * @param limit max hosts to return (0 = all). @param useDoh enrich via DoH.
     */
    suspend fun resolveHosts(
        domains: List<String>,
        useV4: Boolean = true,
        useV6: Boolean = false,
        useDoh: Boolean = true,
        limit: Int = 0,
    ): List<ResolvedHost> = coroutineScope {
        val cleaned = domains.asSequence()
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        if (cleaned.isEmpty()) return@coroutineScope emptyList()

        val perDomain = cleaned.map { domain ->
            async(Dispatchers.IO) {
                domain to runCatching { resolveOne(domain, useV4, useV6, useDoh) }.getOrDefault(emptyList())
            }
        }.awaitAll()

        // Round-robin interleave: take the i-th IP of every domain before moving
        // on to the (i+1)-th, so an early cut-off still covers a broad set. The
        // LinkedHashMap keeps that order and first-domain-wins for shared IPs.
        val byIp = LinkedHashMap<String, String>()
        var i = 0
        var progressed = true
        while (progressed) {
            progressed = false
            for ((domain, ips) in perDomain) {
                if (i < ips.size) { byIp.putIfAbsent(ips[i], domain); progressed = true }
            }
            i++
        }
        val list = byIp.entries.map { ResolvedHost(it.key, it.value) }
        if (limit > 0) list.take(limit) else list
    }

    /** Convenience: just the IP literals from [resolveHosts]. */
    suspend fun resolve(
        domains: List<String>,
        useV4: Boolean = true,
        useV6: Boolean = false,
        useDoh: Boolean = true,
        limit: Int = 0,
    ): List<String> = resolveHosts(domains, useV4, useV6, useDoh, limit).map { it.ip }

    private fun resolveOne(domain: String, useV4: Boolean, useV6: Boolean, useDoh: Boolean): List<String> {
        val acc = LinkedHashSet<String>()
        // Platform resolver (active network DNS).
        runCatching {
            for (addr in InetAddress.getAllByName(domain)) {
                val ip = addr.hostAddress ?: continue
                if (accept(ip, useV4, useV6)) acc.add(ip)
            }
        }
        // DNS-over-HTTPS enrichment — independent of the local resolver.
        if (useDoh) {
            for (provider in DOH_PROVIDERS) {
                if (useV4) runCatching { acc.addAll(doh(provider, domain, TYPE_A)) }
                if (useV6) runCatching { acc.addAll(doh(provider, domain, TYPE_AAAA)) }
            }
        }
        return acc.filter { accept(it, useV4, useV6) }
    }

    private fun doh(provider: String, name: String, type: Int): List<String> {
        val url = URL("$provider?name=$name&type=$type")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
            requestMethod = "GET"
            setRequestProperty("accept", "application/dns-json")
        }
        try {
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val answers = JSONObject(body).optJSONArray("Answer") ?: return emptyList()
            val out = ArrayList<String>(answers.length())
            for (j in 0 until answers.length()) {
                val a = answers.optJSONObject(j) ?: continue
                if (a.optInt("type") != type) continue
                val data = a.optString("data").trim()
                if (data.isNotEmpty()) out.add(data)
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun accept(ip: String, useV4: Boolean, useV6: Boolean): Boolean =
        if (ip.contains(':')) useV6 else useV4

    /** Strip scheme/path/port and lower-case, turning a pasted URL into a host. */
    private fun normalize(raw: String): String {
        var s = raw.trim().lowercase()
        if (s.isEmpty() || s.startsWith("#")) return ""
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/').substringBefore(':').trim()
        return s
    }
}
