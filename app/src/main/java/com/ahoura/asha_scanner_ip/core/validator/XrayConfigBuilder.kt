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
        bypassIran: Boolean = false,
        dnsServers: List<String> = emptyList(),
        includeTun: Boolean = false,
    ): JSONObject {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        val inbounds = JSONArray()
        if (includeTun) {
            inbounds.put(buildTunInbound())
        }
        inbounds.put(buildInbound(socksPort, tag = "socks-in"))
        inbounds.put(buildHttpInbound(httpPort, tag = "http-in"))
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(buildOutbound(proxy, candidateIp ?: proxy.address))
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().apply {
                put("domainStrategy", "UseIP")
            })
            put("streamSettings", JSONObject().apply {
                put("sockopt", JSONObject().apply {
                    put("domainStrategy", "UseIP")
                    put("happyEyeballs", JSONObject().apply {
                        put("tryDelayMs", 250)
                        put("interleave", 2)
                    })
                })
            })
        })
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject())
        })
        root.put("outbounds", outbounds)

        val routing = JSONObject()
        routing.put("domainStrategy", if (proxy.protocol == Protocol.STORMDNS) "AsIs" else "IPIfNonMatch")
        val rules = JSONArray()
        if (bypassIran) {
            // Iranian split tunneling: every .ir domain + the curated Iranian
            // services geosite, plus Iranian IP ranges, bypass the tunnel so
            // banks / government / local apps keep working at native speed
            // while the VPN is up. Loads the trimmed geoip.dat / geosite.dat
            // bundled in assets (entries "IR" / "ir"; loaders match fold-case).
            rules.put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray(listOf("domain:ir", "geosite:category-ir", "geosite:ir")))
                put("outboundTag", "direct")
            })
            rules.put(JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray(listOf("geoip:ir")))
                put("outboundTag", "direct")
            })
        }
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
        // First server wins for xray's own lookups; the DNS Tuner's fastest
        // resolver leads when set. Default mirrors the previous hardcoded list.
        dns.put(
            "servers",
            JSONArray(dnsServers.ifEmpty { listOf("1.1.1.1", "8.8.8.8", "https://cloudflare-dns.com/dns-query") }),
        )
        root.put("dns", dns)

        // Traffic stats: without these policy flags xray-core never registers
        // the per-outbound uplink/downlink counters, so queryAllOutboundTrafficStats()
        // returns an empty string and the VPN screen shows "—" forever. This
        // mirrors the stats/policy block v2rayNG ships in every client config.
        root.put("stats", JSONObject())
        root.put("policy", JSONObject().apply {
            put("levels", JSONObject().put("0", JSONObject().apply {
                put("statsUserUplink", true)
                put("statsUserDownlink", true)
            }))
            put("system", JSONObject().apply {
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        })

        return root
    }

    fun buildClientVpnJson(
        proxy: ProxyConfig,
        candidateIp: String? = null,
        socksPort: Int = 10808,
        httpPort: Int = 10809,
        bypassIran: Boolean = false,
        dnsServers: List<String> = emptyList(),
        includeTun: Boolean = false,
    ): String = buildClientVpnConfig(proxy, candidateIp, socksPort, httpPort, bypassIran, dnsServers, includeTun).toString(2)

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
            put("destOverride", JSONArray(listOf("fakedns", "http", "tls", "quic")))
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
                // XTLS flow (xtls-rprx-vision) is only valid over Reality — a
                // Vision flow on plain TLS makes xray-core exit at config load,
                // which killed every custom connect with such a link.
                if (proxy.flow.isNotBlank() && proxy.security.equals("reality", ignoreCase = true)) {
                    user.put("flow", proxy.flow)
                }
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
                if (proxy.flow.isNotBlank() && proxy.security.equals("reality", ignoreCase = true)) {
                    server.put("flow", proxy.flow)
                }
                settings.put("servers", JSONArray().put(server))
            }
            Protocol.STORMDNS -> {
                outbound.put("protocol", "socks")
                val server = JSONObject()
                    .put("address", "127.0.0.1")
                    .put("port", com.ahoura.asha_scanner_ip.core.storm.StormDnsProcessManager.SOCKS_PORT)
                settings.put("servers", JSONArray().put(server))
            }
        }
        outbound.put("settings", settings)
        outbound.put("streamSettings", buildStreamSettings(proxy))
        return outbound
    }

    private fun buildStreamSettings(proxy: ProxyConfig): JSONObject {
        if (proxy.protocol == Protocol.STORMDNS) {
            return JSONObject().apply {
                put("network", "tcp")
                put("security", "none")
            }
        }
        val stream = JSONObject()
        stream.put("network", proxy.network.ifBlank { "tcp" })
        stream.put("security", proxy.security.ifBlank { "none" })

        when (proxy.security.lowercase()) {
            "tls" -> stream.put("tlsSettings", JSONObject().apply {
                put("serverName", proxy.effectiveSni())
                put("allowInsecure", proxy.allowInsecure)
                if (proxy.fingerprint.isNotBlank()) put("fingerprint", proxy.fingerprint)
                if (proxy.alpn.isNotEmpty()) put("alpn", JSONArray(proxy.alpn))
                if (proxy.cipherSuites.isNotBlank()) put("cipherSuites", proxy.cipherSuites)
            })
            "reality" -> stream.put("realitySettings", JSONObject().apply {
                put("serverName", proxy.effectiveSni())
                if (proxy.fingerprint.isNotBlank()) put("fingerprint", proxy.fingerprint)
                if (proxy.publicKey.isNotBlank()) put("publicKey", proxy.publicKey)
                if (proxy.shortId.isNotBlank()) put("shortId", proxy.shortId)
                if (proxy.cipherSuites.isNotBlank()) put("cipherSuites", proxy.cipherSuites)
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

        if (proxy.dialMode.isNotBlank()) {
            stream.put("sockopt", JSONObject().apply {
                put("dialMode", proxy.dialMode)
            })
        }

        return stream
    }
}
