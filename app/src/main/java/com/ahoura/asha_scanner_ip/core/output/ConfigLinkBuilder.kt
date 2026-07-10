package com.ahoura.asha_scanner_ip.core.output

import com.ahoura.asha_scanner_ip.core.model.ProxyConfig

/**
 * Produces a ready-to-use `vless://` / `trojan://` share link with a scanned
 * clean IP and port swapped in.
 *
 * It surgically replaces the host:port part of the original link to preserve
 * all other parameters (path, header, uuid, etc.), and injects `sni` if 
 * missing to ensure TLS handshakes work with bare IPs.
 */
object ConfigLinkBuilder {

    fun withAddress(proxy: ProxyConfig, ip: String, port: Int): String {
        val raw = proxy.raw
        if (raw.isBlank()) return "$ip:$port"

        // 1. Separate the fragment (#remark)
        val hashIdx = raw.indexOf('#')
        var base = if (hashIdx >= 0) raw.substring(0, hashIdx) else raw
        val frag = if (hashIdx >= 0) raw.substring(hashIdx) else ""

        // 2. Replace host:port surgically. 
        // VLESS/Trojan format: protocol://credential@host:port?query
        val atIdx = base.indexOf('@')
        if (atIdx >= 0) {
            val qIdx = base.indexOf('?', atIdx)
            val hostPortEnd = if (qIdx >= 0) qIdx else base.length
            
            val prefix = base.substring(0, atIdx + 1)
            val suffix = base.substring(hostPortEnd)
            
            val newHostPort = if (ip.contains(":")) "[$ip]:$port" else "$ip:$port"
            base = prefix + newHostPort + suffix
        }

        // 3. Ensure SNI and Security for Cloudflare/TLS
        // If we're using a bare IP, we MUST have SNI if TLS is used.
        val originalDomain = proxy.address
        if (originalDomain.isNotBlank() && !isIpLiteral(originalDomain)) {
            val extra = ArrayList<String>()
            
            // Check if it's a known TLS port or security is already set to tls/reality
            val isTlsPort = isCloudflareTlsPort(port)
            val currentSecurity = getParam(base, "security")?.lowercase() ?: ""
            val isTls = currentSecurity == "tls" || currentSecurity == "reality" || (currentSecurity.isEmpty() && isTlsPort)

            // If it should be TLS but security is missing/empty, fix it
            if (isTls && (currentSecurity.isEmpty() || currentSecurity == "none")) {
                base = setParam(base, "security", "tls")
            }

            // Injected SNI if missing
            if (isTls && !containsParam(base, "sni")) {
                extra.add("sni=$originalDomain")
            }
            
            // Ensure host header for WS/gRPC if missing
            val network = getParam(base, "type")?.lowercase() ?: "tcp"
            val isHttpLike = network == "ws" || network == "grpc" || network.contains("http")
            if (isHttpLike && !containsParam(base, "host")) {
                extra.add("host=$originalDomain")
            }

            if (extra.isNotEmpty()) {
                base += if (base.contains("?")) "&" else "?"
                base += extra.joinToString("&")
            }
        }

        // 4. Update the fragment with the new IP
        val newFrag = if (frag.isEmpty()) "#$ip" else "$frag-$ip"
        return base + newFrag
    }

    private fun containsParam(url: String, name: String): Boolean =
        Regex("[?&]$name=", RegexOption.IGNORE_CASE).containsMatchIn(url)

    private fun getParam(url: String, name: String): String? {
        val match = Regex("[?&]$name=([^&?#]*)", RegexOption.IGNORE_CASE).find(url)
        return match?.groupValues?.get(1)
    }

    private fun setParam(url: String, name: String, value: String): String {
        return if (containsParam(url, name)) {
            url.replace(Regex("([?&])$name=[^&?#]*", RegexOption.IGNORE_CASE), "$1$name=$value")
        } else {
            url + (if (url.contains("?")) "&" else "?") + "$name=$value"
        }
    }

    private fun isIpLiteral(s: String): Boolean =
        s.contains(":") || Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(s)

    private fun isCloudflareTlsPort(port: Int): Boolean =
        port in listOf(443, 2053, 2083, 2087, 2095, 2096, 8443)
}
