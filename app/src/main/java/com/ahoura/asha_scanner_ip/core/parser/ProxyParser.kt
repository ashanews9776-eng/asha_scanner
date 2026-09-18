package com.ahoura.asha_scanner_ip.core.parser

import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

/**
 * Parses `vless://`, `trojan://`, and `stormdns://` share links into a [ProxyConfig].
 *
 * Supports VLESS/Trojan proxy configs as well as WhiteDNS/MasterDNS/CottenDNS
 * DNS Tunnel profiles (`stormdns://`, `masterdns://`, `cottendns://`).
 */
object ProxyParser {

    class ParseException(message: String) : Exception(message)

    fun parse(rawInput: String): ProxyConfig {
        val raw = rawInput.trim()
        val scheme = raw.substringBefore("://", missingDelimiterValue = "").lowercase()
        return when (Protocol.fromScheme(scheme)) {
            Protocol.VLESS -> parseVless(raw)
            Protocol.TROJAN -> parseTrojan(raw)
            Protocol.STORMDNS -> parseStormDns(raw)
            else -> throw ParseException("Unsupported link. Expected vless://, trojan://, or stormdns://")
        }
    }

    /** Quick check used by the UI to enable/disable the start button. */
    fun isSupported(raw: String): Boolean {
        val s = raw.trim().substringBefore("://", "").lowercase()
        return Protocol.fromScheme(s) != null
    }

    private fun parseVless(raw: String): ProxyConfig {
        val schemeSep = raw.indexOf("://")
        val body = if (schemeSep >= 0) raw.substring(schemeSep + 3) else raw
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
            cipherSuites = q["cs"] ?: q["cipherSuites"] ?: "",
            dialMode = q["dialMode"] ?: "",
            remark = parts.remark,
            raw = raw,
        )
    }

    private fun parseTrojan(raw: String): ProxyConfig {
        val schemeSep = raw.indexOf("://")
        val body = if (schemeSep >= 0) raw.substring(schemeSep + 3) else raw
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
            cipherSuites = q["cs"] ?: q["cipherSuites"] ?: "",
            dialMode = q["dialMode"] ?: "",
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

    /**
     * Parses `stormdns://<payload>`, `masterdns://<payload>`, or `cottendns://<payload>`.
     * Decodes the Base64/Base64Url JSON payload matching the WhiteDNS profile specification.
     */
    fun parseStormDns(raw: String): ProxyConfig {
        val schemeSep = raw.indexOf("://")
        if (schemeSep < 0) throw ParseException("Missing scheme separator in DNS profile link")
        val rawPayload = raw.substring(schemeSep + 3).trim()
        val cleanPayload = rawPayload.substringBefore('#').substringBefore('?')
        if (cleanPayload.isBlank()) throw ParseException("DNS profile link payload is empty")

        val padded = cleanPayload.padEnd(cleanPayload.length + ((4 - cleanPayload.length % 4) % 4), '=')
        val bytes = decodeBase64Safe(padded) ?: throw ParseException("DNS profile payload is not valid Base64")

        val jsonString = bytes.toString(Charsets.UTF_8)
        val root = runCatching { JSONObject(jsonString) }.getOrElse {
            throw ParseException("Malformed JSON in DNS profile: ${it.message}")
        }

        val profileObj = root.optJSONObject("profile")
        val serverObj = profileObj?.optJSONObject("server") ?: root.optJSONObject("server")

        val domain = serverObj?.optString("domain")?.takeIf { it.isNotBlank() }
            ?: serverObj?.optJSONArray("domains")?.optString(0)?.takeIf { it.isNotBlank() }
            ?: root.optString("domain").takeIf { it.isNotBlank() }
            ?: throw ParseException("Missing server domain in DNS profile")
        val encryptionKey = serverObj?.optString("encryption_key")
            ?: root.optString("encryption_key").ifBlank { throw ParseException("Missing encryption key in DNS profile") }
        val encryptionMethod = serverObj?.optInt("encryption_method", 1)
            ?: root.optInt("encryption_method", 1)
        val name = profileObj?.optString("name")
            ?: root.optString("name", domain)

        return ProxyConfig(
            protocol = Protocol.STORMDNS,
            address = domain.trim().trimEnd('.'),
            port = 53,
            password = encryptionKey.trim(),
            encryption = encryptionMethod.toString(),
            remark = name.ifBlank { domain },
            raw = raw,
        )
    }

    /**
     * Exports a StormDNS profile into a shareable `stormdns://` URI matching WhiteDNS schema.
     */
    fun exportStormDns(
        name: String,
        domain: String,
        encryptionKey: String,
        encryptionMethod: Int = 1,
    ): String {
        val root = JSONObject().apply {
            put("schema", "whitedns.profile")
            put("version", 1)
            put("profile", JSONObject().apply {
                put("name", name.ifBlank { domain })
                put("server", JSONObject().apply {
                    put("domain", domain.trim().trimEnd('.'))
                    put("encryption_key", encryptionKey.trim())
                    put("encryption_method", encryptionMethod.coerceIn(0, 5))
                })
            })
        }
        val encoded = encodeBase64Safe(root.toString().toByteArray(Charsets.UTF_8))
        return "stormdns://$encoded"
    }

    private fun decodeBase64Safe(s: String): ByteArray? {
        return runCatching {
            android.util.Base64.decode(s, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
        }.recoverCatching {
            android.util.Base64.decode(s, android.util.Base64.DEFAULT)
        }.recoverCatching {
            java.util.Base64.getUrlDecoder().decode(s)
        }.recoverCatching {
            java.util.Base64.getDecoder().decode(s)
        }.getOrNull()
    }

    private fun encodeBase64Safe(bytes: ByteArray): String {
        return runCatching {
            android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            ).trim()
        }.recoverCatching {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }.getOrDefault("")
    }
}


