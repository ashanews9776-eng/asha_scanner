package com.ahoura.asha_scanner_ip.core.parser

import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import java.net.URLDecoder

/**
 * Parses `vless://` and `trojan://` share links into a [ProxyConfig].
 *
 * Kotlin port of SenPaiScanner's `internal/xraytest/parser.go`, including the
 * recovery paths for malformed links (missing `?` between port and query, IPv6
 * bracket handling). Tolerant by design — real-world configs are messy.
 */
object ProxyParser {

    class ParseException(message: String) : Exception(message)

    fun parse(rawInput: String): ProxyConfig {
        val raw = rawInput.trim()
        val scheme = raw.substringBefore("://", missingDelimiterValue = "").lowercase()
        return when (Protocol.fromScheme(scheme)) {
            Protocol.VLESS -> parseVless(raw)
            Protocol.TROJAN -> parseTrojan(raw)
            else -> throw ParseException("Unsupported link. Expected vless:// or trojan://")
        }
    }

    /** Quick check used by the UI to enable/disable the start button. */
    fun isSupported(raw: String): Boolean {
        val s = raw.trim().substringBefore("://", "").lowercase()
        return Protocol.fromScheme(s) != null
    }

    private fun parseVless(raw: String): ProxyConfig {
        val body = raw.removePrefix("vless://")
        val parts = splitLink(body)
        if (parts.credential.isBlank()) throw ParseException("VLESS link missing UUID")
        val q = parts.query
        val security = q["security"] ?: "none"
        val network = (q["type"] ?: "tcp").lowercase()
        return ProxyConfig(
            protocol = Protocol.VLESS,
            address = parts.host,
            port = parts.port,
            uuid = parts.credential,
            encryption = q["encryption"] ?: "none",
            flow = q["flow"] ?: "",
            security = security,
            sni = q["sni"] ?: q["peer"] ?: "",
            fingerprint = q["fp"] ?: "",
            alpn = splitAlpn(q["alpn"]),
            allowInsecure = (q["allowInsecure"] ?: q["insecure"]) == "1",
            network = network,
            path = decode(q["path"]) ?: "/",
            hostHeader = decode(q["host"]) ?: "",
            serviceName = decode(q["serviceName"]) ?: "",
            mode = q["mode"] ?: "",
            publicKey = q["pbk"] ?: "",
            shortId = q["sid"] ?: "",
            remark = parts.remark,
            raw = raw,
        )
    }

    private fun parseTrojan(raw: String): ProxyConfig {
        val body = raw.removePrefix("trojan://")
        val parts = splitLink(body)
        if (parts.credential.isBlank()) throw ParseException("Trojan link missing password")
        val q = parts.query
        val security = q["security"] ?: "tls" // trojan defaults to TLS
        val network = (q["type"] ?: "tcp").lowercase()
        return ProxyConfig(
            protocol = Protocol.TROJAN,
            address = parts.host,
            port = parts.port,
            password = parts.credential,
            security = security,
            sni = q["sni"] ?: q["peer"] ?: "",
            fingerprint = q["fp"] ?: "",
            alpn = splitAlpn(q["alpn"]),
            allowInsecure = (q["allowInsecure"] ?: q["insecure"]) == "1",
            network = network,
            path = decode(q["path"]) ?: "/",
            hostHeader = decode(q["host"]) ?: "",
            serviceName = decode(q["serviceName"]) ?: "",
            mode = q["mode"] ?: "",
            remark = parts.remark,
            raw = raw,
        )
    }

    // --- internals ----------------------------------------------------------

    private data class LinkParts(
        val credential: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val remark: String,
    )

    /**
     * Splits `credential@host:port?query#remark`. Handles a missing `?` by
     * recovering trailing query params glued onto the port, and IPv6 brackets.
     */
    private fun splitLink(input: String): LinkParts {
        var s = input

        // 1) remark from last '#'
        var remark = ""
        val hash = s.lastIndexOf('#')
        if (hash >= 0) {
            remark = decode(s.substring(hash + 1)) ?: ""
            s = s.substring(0, hash)
        }

        // 2) query from first '?'
        var query = emptyMap<String, String>()
        val qmark = s.indexOf('?')
        if (qmark >= 0) {
            query = parseQuery(s.substring(qmark + 1))
            s = s.substring(0, qmark)
        }

        // 3) credential before first '@'
        val at = s.indexOf('@')
        if (at < 0) throw ParseException("link missing '@' separator")
        val credential = decode(s.substring(0, at)) ?: ""
        val hostPort = s.substring(at + 1)

        // 4) host:port (IPv6-aware), with recovery for query glued to the port
        val (host, port, recovered) = splitHostPort(hostPort)
        if (recovered.isNotEmpty()) {
            query = parseQuery(recovered) + query
        }
        return LinkParts(credential, host, port, query, remark)
    }

    /** Returns Triple(host, port, recoveredQueryString). */
    private fun splitHostPort(hp: String): Triple<String, Int, String> {
        var host: String
        var portPart: String
        if (hp.startsWith("[")) {
            val close = hp.indexOf(']')
            if (close < 0) throw ParseException("malformed IPv6 host: $hp")
            host = hp.substring(1, close)
            portPart = hp.substring(close + 1).removePrefix(":")
        } else {
            val colon = hp.lastIndexOf(':')
            if (colon < 0) throw ParseException("link missing port: $hp")
            host = hp.substring(0, colon)
            portPart = hp.substring(colon + 1)
        }
        // recover: port digits may be immediately followed by query (no '?')
        val digits = portPart.takeWhile { it.isDigit() }
        if (digits.isEmpty()) throw ParseException("invalid port in: $hp")
        val recovered = portPart.substring(digits.length).removePrefix("&").removePrefix("?")
        return Triple(host, digits.toInt(), recovered)
    }

    private fun parseQuery(qs: String): Map<String, String> {
        if (qs.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (pair in qs.split('&')) {
            if (pair.isBlank()) continue
            val eq = pair.indexOf('=')
            if (eq < 0) {
                map[decode(pair) ?: pair] = ""
            } else {
                val k = decode(pair.substring(0, eq)) ?: continue
                val v = decode(pair.substring(eq + 1)) ?: ""
                map[k] = v
            }
        }
        return map
    }

    private fun splitAlpn(v: String?): List<String> =
        v?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun decode(s: String?): String? =
        if (s == null) null else try {
            URLDecoder.decode(s, "UTF-8")
        } catch (_: Exception) {
            s
        }
}
