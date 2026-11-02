package com.ahoura.asha_scanner_ip.core.aether

/**
 * Builds the command line for the bundled Aether v2.4.0 CLI
 * (`libaether_v24.so`, upstream CluvexStudio/Aether v2.4.0).
 *
 * Supports WireGuard, MASQUE, WOM (WireGuard over MASQUE), GOOL (WARP-in-WARP),
 * and MIM (MASQUE-in-MASQUE), with auto-scan (`--scan balanced`) and fast
 * gateway reconnect (`--quick-reconnect`).
 */
data class AetherCliSettings(
    val protocol: String = "wireguard",          // wireguard | masque | wom | gool | mim
    val socksPort: Int = 10822,
    val identityFile: String = "",               // Optional specific file; empty = automatic from working directory
    val upstream: String = "",                   // full URI, e.g. socks5://host:port
    val peer: String = "",                       // gateway endpoint override (empty = auto gateway scan if scanMode set)
    val scanMode: String = "",                   // turbo | balanced | thorough | verified | ironclad
    val quickReconnect: Boolean = false,
    val apiAddress: String = "",                 // host or host:port (--api-address)
    val apiHost: String = "",                    // SNI-less host (--api-host)
    val apiSni: String = "",                     // --api-sni
    val masqueSni: String = "",                  // --masque-sni
    val fragment: Boolean = false,               // default OFF per v2.4.0
    val fragmentSize: Int = 0,
    val fragmentDelay: Int = 0,
    val team: String = "",                       // Zero Trust organization
    val tlsGroups: String = "",
    val tlsKeyShares: String = "",
    val tlsSigalgs: String = "",
    val tls13Ciphers: String = "",
    val tlsCertCompression: String = "",         // zlib | brotli | zstd (colon list)
    val tlsAlps: String = "",                    // new | old
    val enableGreaseSigalgs: Boolean = false,
    val enableEchGrease: Boolean = false,
    val tlsVerify: Boolean = false,
    val exitLoc: String = "",                    // exit country filter, e.g. "!IR,AZ,RU" or "DE,SE"
)

object AetherCliConfig {

    val PROTOCOLS = listOf("wireguard", "wg", "masque", "wom", "wg-over-masque", "gool", "mim")

    fun buildArgs(s: AetherCliSettings): List<String> {
        val protoRaw = s.protocol.trim().lowercase()
        require(protoRaw in PROTOCOLS) { "unsupported protocol: $protoRaw" }
        require(s.socksPort in 1024..65535) { "SOCKS port out of range" }

        val args = mutableListOf<String>()

        if (s.identityFile.isNotBlank()) {
            args += listOf("--config", s.identityFile)
        }

        val effectiveProto = when (protoRaw) {
            "wom", "wg-over-masque" -> "gool"
            else -> protoRaw
        }

        args += listOf(
            "--bind", "127.0.0.1:${s.socksPort}",
            "--protocol", effectiveProto,
            "--log-level", "info",
        )

        if (protoRaw == "gool" || protoRaw == "warp-in-warp" || protoRaw == "wiw") {
            args += "--gool-classic"
        }

        fun add(flag: String, value: String) {
            val v = value.trim()
            if (v.isNotEmpty()) args += listOf(flag, v)
        }

        add("--upstream", s.upstream)

        // Endpoint selection vs Auto Scan
        val peerStr = s.peer.trim()
        if (peerStr.isNotEmpty()) {
            when (protoRaw) {
                "gool", "warp-in-warp", "wiw" -> {
                    if (peerStr.contains(",")) {
                        val outer = peerStr.substringBefore(",").trim()
                        val inner = peerStr.substringAfter(",").trim()
                        if (outer.isNotEmpty()) add("--wiw-outer", outer)
                        if (inner.isNotEmpty()) add("--wiw-inner", inner)
                    } else {
                        add("--peer", peerStr)
                    }
                }
                "mim", "masque-in-masque" -> {
                    if (peerStr.contains(",")) {
                        val outer = peerStr.substringBefore(",").trim()
                        val inner = peerStr.substringAfter(",").trim()
                        if (outer.isNotEmpty()) add("--mim-outer", outer)
                        if (inner.isNotEmpty()) add("--mim-inner", inner)
                    } else {
                        add("--peer", peerStr)
                    }
                }
                else -> {
                    add("--peer", peerStr)
                }
            }
        } else if (s.scanMode.isNotBlank()) {
            add("--scan", s.scanMode)
            if (s.quickReconnect) {
                args += "--quick-reconnect"
            }
        }

        add("--exit-loc", s.exitLoc)
        add("--api-address", s.apiAddress)
        add("--api-host", s.apiHost)
        add("--api-sni", s.apiSni)
        add("--masque-sni", s.masqueSni)
        add("--team", s.team)

        if (s.fragment) {
            args += "--fragment"
            if (s.fragmentSize > 0) args += listOf("--fragment-size", s.fragmentSize.toString())
            if (s.fragmentDelay > 0) args += listOf("--fragment-delay", s.fragmentDelay.toString())
        }

        add("--tls-groups", s.tlsGroups)
        add("--tls-key-shares", s.tlsKeyShares)
        add("--tls-sigalgs", s.tlsSigalgs)
        add("--tls13-ciphers", s.tls13Ciphers)
        add("--tls-cert-compression", s.tlsCertCompression)
        add("--tls-alps", s.tlsAlps)

        if (s.enableGreaseSigalgs) args += "--enable-grease-sigalgs"
        if (s.enableEchGrease) args += "--enable-ech-grease"
        if (s.tlsVerify) args += "--tls-verify"

        return args
    }

    // ── JSON persistence ──

    fun toJson(s: AetherCliSettings): String = org.json.JSONObject().apply {
        put("protocol", s.protocol)
        put("upstream", s.upstream)
        put("peer", s.peer)
        put("scanMode", s.scanMode)
        put("quickReconnect", s.quickReconnect)
        put("exitLoc", s.exitLoc)
        put("apiAddress", s.apiAddress)
        put("apiHost", s.apiHost)
        put("apiSni", s.apiSni)
        put("masqueSni", s.masqueSni)
        put("fragment", s.fragment)
        put("fragmentSize", s.fragmentSize)
        put("fragmentDelay", s.fragmentDelay)
        put("team", s.team)
        put("tlsGroups", s.tlsGroups)
        put("tlsKeyShares", s.tlsKeyShares)
        put("tlsSigalgs", s.tlsSigalgs)
        put("tls13Ciphers", s.tls13Ciphers)
        put("tlsCertCompression", s.tlsCertCompression)
        put("tlsAlps", s.tlsAlps)
        put("enableGreaseSigalgs", s.enableGreaseSigalgs)
        put("enableEchGrease", s.enableEchGrease)
        put("tlsVerify", s.tlsVerify)
    }.toString()

    fun fromJson(json: String, identityFile: String = "", socksPort: Int = 10822): AetherCliSettings {
        val o = runCatching { org.json.JSONObject(json) }.getOrElse {
            return AetherCliSettings(identityFile = identityFile, socksPort = socksPort)
        }
        return AetherCliSettings(
            protocol = o.optString("protocol", "wireguard").ifBlank { "wireguard" },
            socksPort = socksPort,
            identityFile = identityFile,
            upstream = o.optString("upstream"),
            peer = o.optString("peer"),
            scanMode = o.optString("scanMode"),
            quickReconnect = o.optBoolean("quickReconnect", false),
            exitLoc = o.optString("exitLoc"),
            apiAddress = o.optString("apiAddress"),
            apiHost = o.optString("apiHost"),
            apiSni = o.optString("apiSni"),
            masqueSni = o.optString("masqueSni"),
            fragment = o.optBoolean("fragment", false),
            fragmentSize = o.optInt("fragmentSize", 0),
            fragmentDelay = o.optInt("fragmentDelay", 0),
            team = o.optString("team"),
            tlsGroups = o.optString("tlsGroups"),
            tlsKeyShares = o.optString("tlsKeyShares"),
            tlsSigalgs = o.optString("tlsSigalgs"),
            tls13Ciphers = o.optString("tls13Ciphers"),
            tlsCertCompression = o.optString("tlsCertCompression"),
            tlsAlps = o.optString("tlsAlps"),
            enableGreaseSigalgs = o.optBoolean("enableGreaseSigalgs", false),
            enableEchGrease = o.optBoolean("enableEchGrease", false),
            tlsVerify = o.optBoolean("tlsVerify", false),
        )
    }
}
