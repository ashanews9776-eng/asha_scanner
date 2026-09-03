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
     * Builds a full client config suitable for VPN routing, including SOCKS5, HTTP inbounds,
     * direct rules for LAN, and DNS configuration.
     */
    fun buildClientVpnConfig(
        proxy: ProxyConfig,
        candidateIp: String? = null,
        socksPort: Int = 10808,
        httpPort: Int = 10809,
    ): JSONObject {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        val inbounds = JSONArray()
        inbounds.put(buildTunInbound())
        inbounds.put(buildInbound(socksPort, tag = "socks-in"))
        inbounds.put(buildHttpInbound(httpPort, tag = "http-in"))
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(buildOutbound(proxy, candidateIp ?: proxy.address))
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject())
        })
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject())
        })
        root.put("outbounds", outbounds)

        val routing = JSONObject()
        routing.put("domainStrategy", "IPIfNonMatch")
        val rules = JSONArray()
        // Direct LAN / local traffic
        rules.put(JSONObject().apply {
            put("type", "field")
            put("ip", JSONArray(listOf(
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "127.0.0.0/8",
                "100.64.0.0/10",
                "169.254.0.0/16",
                "::1/128",
                "fc00::/7",
                "fe80::/10"
            )))
            put("outboundTag", "direct")
        })
        // Proxy everything else
        rules.put(JSONObject().apply {
            put("type", "field")
            put("network", "tcp,udp")
            put("outboundTag", "proxy")
        })
        routing.put("rules", rules)
        root.put("routing", routing)

        val dns = JSONObject()
        dns.put("servers", JSONArray(listOf("1.1.1.1", "8.8.8.8", "https://cloudflare-dns.com/dns-query")))
        root.put("dns", dns)

        return root
    }

    fun buildClientVpnJson(
        proxy: ProxyConfig,
        candidateIp: String? = null,
        socksPort: Int = 10808,
        httpPort: Int = 10809,
    ): String = buildClientVpnConfig(proxy, candidateIp, socksPort, httpPort).toString(2)

    private fun buildTunInbound(): JSONObject = JSONObject().apply {
        put("tag", "tun")
        put("protocol", "tun")
        put("settings", JSONObject().apply {
            put("name", "xray0")
            put("MTU", 1500)
            put("userLevel", 8)
        })
        put("sniffing", JSONObject().apply {
            put("enabled", true)
            put("destOverride", JSONArray(listOf("http", "tls", "quic")))
        })
    }

    private fun buildHttpInbound(httpPort: Int, tag: String = "http-in"): JSONObject = JSONObject().apply {
        put("tag", tag)
        put("listen", "127.0.0.1")
        put("port", httpPort)
        put("protocol", "http")
        put("settings", JSONObject().put("auth", "noauth"))
    }

    private fun buildInbound(socksPort: Int, tag: String = "socks-in"): JSONObject = JSONObject().apply {
        put("tag", tag)
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
