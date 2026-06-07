package com.ahoura.asha_scanner_ip.core.validator

import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds an xray-core JSON config that routes a local SOCKS inbound through the
 * user's VLESS/Trojan outbound, but pointed at a *candidate IP* instead of the
 * original domain. Kotlin port of SenPaiScanner's `internal/xraytest/builder.go`.
 *
 * This is the exact config an embedded xray-core (libv2ray / AndroidLibXrayLite)
 * would consume for true end-to-end Phase-2 validation. It is fully implemented
 * here so the project is feature-complete with the original; wiring the native
 * runtime only requires feeding [build] output to the xray instance (see
 * [XrayValidator]).
 */
object XrayConfigBuilder {

    /**
     * @param candidateIp the Cloudflare edge IP to test (replaces proxy.address)
     * @param socksPort   local SOCKS5 listen port for the test client
     */
    fun build(proxy: ProxyConfig, candidateIp: String, socksPort: Int): JSONObject {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "none"))
        root.put("inbounds", JSONArray().put(buildInbound(socksPort)))
        root.put("outbounds", JSONArray().put(buildOutbound(proxy, candidateIp)))
        return root
    }

    fun buildJson(proxy: ProxyConfig, candidateIp: String, socksPort: Int): String =
        build(proxy, candidateIp, socksPort).toString(2)

    private fun buildInbound(socksPort: Int): JSONObject = JSONObject().apply {
        put("tag", "socks-in")
        put("listen", "127.0.0.1")
        put("port", socksPort)
        put("protocol", "socks")
        put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
    }

    private fun buildOutbound(proxy: ProxyConfig, candidateIp: String): JSONObject {
        val outbound = JSONObject().put("tag", "proxy")
        val settings = JSONObject()
        when (proxy.protocol) {
            Protocol.VLESS -> {
                outbound.put("protocol", "vless")
                val user = JSONObject()
                    .put("id", proxy.uuid)
                    .put("encryption", proxy.encryption.ifBlank { "none" })
                if (proxy.flow.isNotBlank()) user.put("flow", proxy.flow)
                val vnext = JSONObject()
                    .put("address", candidateIp)
                    .put("port", proxy.port)
                    .put("users", JSONArray().put(user))
                settings.put("vnext", JSONArray().put(vnext))
            }
            Protocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                val server = JSONObject()
                    .put("address", candidateIp)
                    .put("port", proxy.port)
                    .put("password", proxy.password)
                if (proxy.flow.isNotBlank()) server.put("flow", proxy.flow)
                settings.put("servers", JSONArray().put(server))
            }
        }
        outbound.put("settings", settings)
        outbound.put("streamSettings", buildStreamSettings(proxy))
        return outbound
    }

    private fun buildStreamSettings(proxy: ProxyConfig): JSONObject {
        val stream = JSONObject()
        stream.put("network", proxy.network.ifBlank { "tcp" })
        stream.put("security", proxy.security.ifBlank { "none" })

        when (proxy.security.lowercase()) {
            "tls" -> stream.put("tlsSettings", JSONObject().apply {
                put("serverName", proxy.effectiveSni())
                put("allowInsecure", proxy.allowInsecure)
                if (proxy.fingerprint.isNotBlank()) put("fingerprint", proxy.fingerprint)
                if (proxy.alpn.isNotEmpty()) put("alpn", JSONArray(proxy.alpn))
            })
            "reality" -> stream.put("realitySettings", JSONObject().apply {
                put("serverName", proxy.effectiveSni())
                if (proxy.fingerprint.isNotBlank()) put("fingerprint", proxy.fingerprint)
                if (proxy.publicKey.isNotBlank()) put("publicKey", proxy.publicKey)
                if (proxy.shortId.isNotBlank()) put("shortId", proxy.shortId)
            })
        }

        when (proxy.network.lowercase()) {
            "ws" -> stream.put("wsSettings", JSONObject().apply {
                put("path", proxy.path.ifBlank { "/" })
                val host = proxy.hostHeader.ifBlank { proxy.effectiveSni() }
                if (host.isNotBlank()) put("headers", JSONObject().put("Host", host))
            })
            "grpc" -> stream.put("grpcSettings", JSONObject().apply {
                put("serviceName", proxy.serviceName)
                put("multiMode", proxy.mode.equals("multi", ignoreCase = true))
            })
            "xhttp", "splithttp" -> stream.put("xhttpSettings", JSONObject().apply {
                put("path", proxy.path.ifBlank { "/" })
                val host = proxy.hostHeader.ifBlank { proxy.effectiveSni() }
                if (host.isNotBlank()) put("host", host)
                if (proxy.mode.isNotBlank()) put("mode", proxy.mode)
            })
        }
        return stream
    }
}
