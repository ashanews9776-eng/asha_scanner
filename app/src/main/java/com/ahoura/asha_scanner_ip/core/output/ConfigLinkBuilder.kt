package com.ahoura.asha_scanner_ip.core.output

import com.ahoura.asha_scanner_ip.core.model.ProxyConfig

/**
 * Produces a ready-to-use `vless://` / `trojan://` share link with a scanned
 * clean IP swapped in as the server address.
 *
 * Keeps the user's original link intact (all params preserved) and only:
 *  - replaces the `@host:` address with the clean IP (IPv6 bracketed), and
 *  - injects `sni=`/`host=` set to the original domain when the link relies on
 *    TLS but didn't specify them — without this the client would (wrongly) use
 *    the bare IP as the SNI and the TLS handshake to Cloudflare would fail.
 *
 * The result is paste-ready in v2rayNG / NekoBox / sing-box.
 */
object ConfigLinkBuilder {

    fun withAddress(proxy: ProxyConfig, ip: String): String {
        val raw = proxy.raw
        if (raw.isBlank()) return ip

        // Split off the #remark fragment so we don't disturb it.
        val hashIdx = raw.indexOf('#')
        var base = if (hashIdx >= 0) raw.substring(0, hashIdx) else raw
        val frag = if (hashIdx >= 0) raw.substring(hashIdx) else ""

        // Swap the address (bracket IPv6).
        val newAddr = if (ip.contains(":")) "[$ip]" else ip
        val oldBracketed = if (proxy.address.contains(":")) "[${proxy.address}]" else proxy.address
        base = when {
            base.contains("@$oldBracketed:") -> base.replaceFirst("@$oldBracketed:", "@$newAddr:")
            base.contains("@${proxy.address}:") -> base.replaceFirst("@${proxy.address}:", "@$newAddr:")
            else -> base
        }

        // Ensure SNI / Host point at the original domain (needed once the address
        // is a bare IP). Only add when missing and the original was a domain.
        val domain = proxy.address
        if (domain.isNotBlank() && !isIpLiteral(domain)) {
            val extra = ArrayList<String>()
            val tlsLike = proxy.security.equals("tls", true) || proxy.security.equals("reality", true)
            if (tlsLike && proxy.sni.isBlank() && !containsParam(base, "sni")) {
                extra.add("sni=$domain")
            }
            val httpLike = proxy.network.equals("ws", true) ||
                proxy.network.equals("xhttp", true) ||
                proxy.network.equals("splithttp", true) ||
                proxy.network.equals("httpupgrade", true)
            if (httpLike && proxy.hostHeader.isBlank() && !containsParam(base, "host")) {
                extra.add("host=$domain")
            }
            if (extra.isNotEmpty()) {
                base += if (base.contains("?")) "&" else "?"
                base += extra.joinToString("&")
            }
        }

        // Tag the remark with the IP so it's identifiable in the client.
        val newFrag = if (frag.isEmpty()) "#$ip" else "$frag-$ip"
        return base + newFrag
    }

    private fun containsParam(url: String, name: String): Boolean =
        Regex("[?&]$name=", RegexOption.IGNORE_CASE).containsMatchIn(url)

    private fun isIpLiteral(s: String): Boolean =
        s.contains(":") || Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(s)
}
