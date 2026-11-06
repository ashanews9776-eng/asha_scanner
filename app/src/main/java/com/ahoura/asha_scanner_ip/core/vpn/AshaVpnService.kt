package com.ahoura.asha_scanner_ip.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import com.ahoura.asha_scanner_ip.core.storm.StormDnsProcessManager
import com.ahoura.asha_scanner_ip.core.validator.XrayConfigBuilder
import com.ahoura.asha_scanner_ip.core.validator.XrayProcessManager
import com.ahoura.asha_scanner_ip.data.SettingsStore
import com.ahoura.asha_scanner_ip.core.guard.AshaCarrier
import com.ahoura.asha_scanner_ip.core.guard.CoreConfig
import com.ahoura.asha_scanner_ip.core.guard.GuardVpnService
import com.ahoura.asha_scanner_ip.core.guard.MtuConfig
import com.ahoura.asha_scanner_ip.core.guard.SplitTunnelSettings
import com.ahoura.asha_scanner_ip.core.guard.TorManager
import com.ahoura.asha_scanner_ip.core.guard.Tun2SocksManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Unified VPN Service: inherits all anti-censorship transports from [GuardVpnService]
 * (WireGuard, MASQUE, WARP-on-WARP, Psiphon, Tor, SHARD), while providing full support
 * for scanned custom proxy profiles (VLESS, Trojan, StormDNS, CottenDNS) via standalone
 * Xray core and badvpn tun2socks.
 */
class AshaVpnService : GuardVpnService() {

    companion object {
        const val ACTION_START = "com.ahoura.asha_scanner_ip.vpn.START"
        const val ACTION_STOP = "com.ahoura.asha_scanner_ip.vpn.STOP"
        const val EXTRA_RAW_CONFIG = "extra_raw_config"
        const val EXTRA_CLEAN_IP = "extra_clean_ip"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
        const val EXTRA_TRANSPORT = "extra_transport"
        const val EXTRA_CHAINED = "extra_chained"
    }

    private var customVpnInterface: ParcelFileDescriptor? = null
    private val stormDnsManager by lazy { StormDnsProcessManager(applicationContext) }
    private val customScope = CoroutineScope(Dispatchers.IO + Job())
    private var customTimerJob: Job? = null
    private var customPingJob: Job? = null
    private var customConnectedStartMs: Long = 0L

    @Volatile
    private var isCustomActive = false

    @Volatile
    private var isConnectedAndBridged = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    // Immediately satisfy Android's foreground service start requirement
                    runCatching { startAsForeground() }
                    System.setProperty("java.net.preferIPv4Stack", "true")
                    System.setProperty("java.net.preferIPv6Addresses", "false")

                    val transport = intent.getStringExtra(EXTRA_TRANSPORT)?.trim()?.lowercase() ?: "custom"
                    val rawConfig = intent.getStringExtra(EXTRA_RAW_CONFIG) ?: ""
                    val cleanIp = intent.getStringExtra(EXTRA_CLEAN_IP)
                    val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Proxy"
                    val explicitChained = if (intent.hasExtra(EXTRA_CHAINED)) intent.getBooleanExtra(EXTRA_CHAINED, false) else null

                    if (transport == "aether24") {
                        // Aether v2.4.0 CLI transport: its own process engine,
                        // TUN bridged via tun2socks — completely independent of
                        // the guard transports and the custom-profile path.
                        stopCustomVpn()
                        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                        prefs.edit().putString("default_protocol", transport).apply()
                        startAetherCliVpn(profileName)
                        return START_NOT_STICKY
                    }

                    if (transport != "custom" && rawConfig.isBlank()) {
                        // Asha Guard transport: wireguard, masque, gool, psiphon, tor, shard
                        stopCustomVpn()
                        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                        prefs.edit().putString("default_protocol", transport).apply()

                        if (!cleanIp.isNullOrBlank()) {
                            if (CoreConfig.isWarpEndpoint(cleanIp) && !cleanIp.contains("192.0.54.") && !cleanIp.contains("192.0.63.")) {
                                prefs.edit().putString("clean_ip", cleanIp.trim()).apply()
                            }
                        }

                        val isPow = transport == "pow"
                        val armed = if (explicitChained != null) {
                            // Persist only when the user made an explicit
                            // choice, and only for transports whose chain flag
                            // actually means the same thing next time.
                            if (transport == "psiphon") prefs.edit().putBoolean("chain_armed", explicitChained).apply()
                            if (transport == "tor") prefs.edit().putBoolean("tor_chain_armed", explicitChained).apply()
                            explicitChained
                        } else {
                            when {
                                // pow is a chain BY CONSTRUCTION, but that must
                                // not leak into the user's psiphon preference:
                                // forcing chain_armed=true here made a later
                                // plain-Psiphon connect start chained.
                                isPow -> true
                                transport == "tor" -> TorManager.chainArmed(this)
                                else -> prefs.getBoolean("chain_armed", false)
                            }
                        }

                        val effectiveProto = if (isPow || (transport == "psiphon" && armed)) {
                            GuardVpnService.CHAIN_PROTOCOL_MARKER.lowercase()
                        } else {
                            transport
                        }

                        // Configure Aether environment variables (Upstream Proxy & WARP-on-WARP hops)
                        val upstream = prefs.getString("upstream_proxy", null)?.trim().orEmpty()
                        if (upstream.isNotBlank()) {
                            runCatching { android.system.Os.setenv("AETHER_UPSTREAM", upstream, true) }
                        } else {
                            runCatching { android.system.Os.unsetenv("AETHER_UPSTREAM") }
                        }

                        val wiwOuter = prefs.getString("wiw_outer", null)?.trim().orEmpty()
                        val wiwInner = prefs.getString("wiw_inner", null)?.trim().orEmpty()
                        if (wiwOuter.isNotBlank()) {
                            runCatching { android.system.Os.setenv("AETHER_WIW_OUTER_PEER", wiwOuter, true) }
                        } else {
                            runCatching { android.system.Os.unsetenv("AETHER_WIW_OUTER_PEER") }
                        }
                        if (wiwInner.isNotBlank()) {
                            runCatching { android.system.Os.setenv("AETHER_WIW_INNER_PEER", wiwInner, true) }
                        } else {
                            runCatching { android.system.Os.unsetenv("AETHER_WIW_INNER_PEER") }
                        }

                        // Aether v2.3.0: Configure AETHER_ENROLL_ADDRESS for direct WARP API key acquisition & registration
                        val manualEndpoint = prefs.getString("manual_endpoint", null)?.trim().orEmpty()
                        val storedCleanIp = prefs.getString("clean_ip", null)?.trim().orEmpty()
                        val rawCleanCandidate = manualEndpoint.takeIf { it.isNotBlank() }
                            ?: cleanIp?.trim()?.takeIf { it.isNotBlank() }
                            ?: storedCleanIp.takeIf { it.isNotBlank() }
                        val rawCleanIp = if (!rawCleanCandidate.isNullOrBlank() &&
                            CoreConfig.isWarpEndpoint(rawCleanCandidate) &&
                            !rawCleanCandidate.contains("192.0.54.") &&
                            !rawCleanCandidate.contains("192.0.63.")) {
                            rawCleanCandidate
                        } else {
                            CoreConfig.DEFAULT_CLEAN_H2_PEERS.first()
                        }

                        val isChained = (transport == "psiphon" && armed) ||
                            (transport == "tor" && TorManager.chainArmed(this)) ||
                            effectiveProto.contains("chain") ||
                            effectiveProto.contains("psiphon-over-warp")

                        val isManual = manualEndpoint.isNotBlank() && CoreConfig.isWarpEndpoint(manualEndpoint)
                        val targetCleanOverride = if (isManual) manualEndpoint else rawCleanCandidate?.takeIf { CoreConfig.isWarpEndpoint(it) }
                        val chosenEnrollIp = targetCleanOverride?.let { CoreConfig.formatPeerForProtocol(it, effectiveProto) }
                        val extraPeers = listOfNotNull(
                            rawCleanCandidate?.takeIf { CoreConfig.isWarpEndpoint(it) }?.let { CoreConfig.formatPeerForProtocol(it, "masque") }
                        )
                        CoreConfig.ensureCleanH2Peers(this, extraPeers)

                        val enrollHost = if (isManual) {
                            manualEndpoint.removeSurrounding("[", "]").substringBefore(":")
                        } else if (!rawCleanCandidate.isNullOrBlank() && CoreConfig.isWarpEndpoint(rawCleanCandidate)) {
                            rawCleanCandidate.removeSurrounding("[", "]").substringBefore(":")
                        } else {
                            "162.159.192.1"
                        }
                        val enrollAddress = if (enrollHost.contains(":")) "[$enrollHost]:443" else "$enrollHost:443"
                        runCatching { android.system.Os.setenv("AETHER_ENROLL_ADDRESS", enrollAddress, true) }

                        val echEnabled = prefs.getBoolean("ech_enabled", false)
                        if (echEnabled) {
                            runCatching { android.system.Os.setenv("AETHER_ECH", "auto", true) }
                        } else {
                            runCatching { android.system.Os.unsetenv("AETHER_ECH") }
                        }

                        val isOuterWarp = effectiveProto.lowercase() in listOf("masque", "mim", "gool", "warp-in-warp", "wom", "wg-over-masque") || isChained

                        if (isOuterWarp) {
                            runCatching {
                                val masqueTransport = prefs.getString("default_masque_transport", "h3")?.lowercase() ?: "h3"
                                if (masqueTransport == "h2" || isChained || effectiveProto.lowercase() in listOf("wom", "wg-over-masque")) {
                                    android.system.Os.setenv("AETHER_MASQUE_HTTP2", "1", true)
                                } else {
                                    android.system.Os.unsetenv("AETHER_MASQUE_HTTP2")
                                }
                                val isMci = AshaCarrier.isMci(this)
                                if (isMci) {
                                    android.system.Os.unsetenv("AETHER_MASQUE_H2_FRAGMENT")
                                    android.system.Os.unsetenv("AETHER_API_FRAGMENT")
                                } else {
                                    android.system.Os.setenv("AETHER_MASQUE_H2_FRAGMENT", "1", true)
                                    android.system.Os.setenv("AETHER_API_FRAGMENT", "1", true)
                                }
                                android.system.Os.setenv("AETHER_MASQUE_STARTUP_SECS", "35", true)
                                android.system.Os.setenv("AETHER_MASQUE_VALIDATE_SECS", "15", true)
                                android.system.Os.setenv("AETHER_WG_VALIDATE_SECS", "15", true)
                                android.system.Os.setenv("AETHER_API_CAMO", "1", true)
                                android.system.Os.setenv("AETHER_MASQUE_H2_LARGE_WINDOWS", "1", true)
                                android.system.Os.setenv("AETHER_MASQUE_H2_SOCKET_TUNING", "1", true)
                                android.system.Os.setenv("AETHER_MASQUE_H2_SPEED_PICK", "1", true)
                                android.system.Os.setenv("AETHER_QUIC_TUNED", "1", true)
                                if (isManual) {
                                    val manualPeer = CoreConfig.formatPeerForProtocol(manualEndpoint, "masque")
                                    if (manualPeer != null) {
                                        android.system.Os.setenv("AETHER_MASQUE_H2_PEER", manualPeer, true)
                                        android.system.Os.setenv("AETHER_PEER", manualPeer, true)
                                    }
                                } else {
                                    android.system.Os.unsetenv("AETHER_PEER")
                                    android.system.Os.unsetenv("AETHER_MASQUE_H2_PEER")
                                    android.system.Os.unsetenv("AETHER_WG_PEER")
                                }
                            }
                        } else if (effectiveProto.lowercase() in listOf("wireguard", "wg")) {
                            runCatching {
                                android.system.Os.unsetenv("AETHER_MASQUE_HTTP2")
                                android.system.Os.unsetenv("AETHER_MASQUE_H2_PEER")
                                val targetWg = if (isManual) manualEndpoint else rawCleanCandidate?.takeIf { CoreConfig.isWarpEndpoint(it) }
                                val wgPeer = targetWg?.let { CoreConfig.formatPeerForProtocol(it, "wireguard") }
                                if (wgPeer != null) {
                                    android.system.Os.setenv("AETHER_PEER", wgPeer, true)
                                    android.system.Os.setenv("AETHER_WG_PEER", wgPeer, true)
                                } else {
                                    android.system.Os.unsetenv("AETHER_PEER")
                                    android.system.Os.unsetenv("AETHER_WG_PEER")
                                }
                            }
                        } else {
                            // Plain direct Psiphon, Tor, SHARD, custom proxy
                            runCatching {
                                android.system.Os.unsetenv("AETHER_MASQUE_HTTP2")
                                android.system.Os.unsetenv("AETHER_MASQUE_H2_PEER")
                                android.system.Os.unsetenv("AETHER_WG_PEER")
                                android.system.Os.unsetenv("AETHER_PEER")
                            }
                        }
                        if (MtuConfig.isCustom(this, MtuConfig.Method.WOM)) {
                            val mtu = MtuConfig.get(this, MtuConfig.Method.WOM).toString()
                            runCatching {
                                android.system.Os.setenv("AETHER_WOM_MTU", mtu, true)
                                android.system.Os.setenv("AETHER_MASQUE_MTU", mtu, true)
                            }
                        }
                        if (MtuConfig.isCustom(this, MtuConfig.Method.MASQUE)) {
                            val mtu = MtuConfig.get(this, MtuConfig.Method.MASQUE).toString()
                            runCatching { android.system.Os.setenv("AETHER_MASQUE_MTU", mtu, true) }
                        }
                        if (MtuConfig.isCustom(this, MtuConfig.Method.WIREGUARD)) {
                            val mtu = MtuConfig.get(this, MtuConfig.Method.WIREGUARD).toString()
                            runCatching { android.system.Os.setenv("AETHER_WG_MTU", mtu, true) }
                        }
                        if (MtuConfig.isCustom(this, MtuConfig.Method.WOW)) {
                            val mtu = MtuConfig.get(this, MtuConfig.Method.WOW).toString()
                            runCatching {
                                android.system.Os.setenv("AETHER_WG_MTU", mtu, true)
                                android.system.Os.setenv("AETHER_MASQUE_MTU", mtu, true)
                            }
                        }
                        runCatching {
                            // Let Go use Android APEX (/apex/com.android.conscrypt/cacerts) & system certs natively
                            android.system.Os.unsetenv("SSL_CERT_DIR")
                        }

                        CoreConfig.refreshPinnedIpsBlocking(this@AshaVpnService, timeoutMs = 800L)
                        val tunnelIntent = Intent(this, AshaVpnService::class.java).apply {
                            action = ACTION_CONNECT
                            val config = CoreConfig.json(this@AshaVpnService, effectiveProto, cleanIpOverride = chosenEnrollIp)
                            putExtra(EXTRA_CONFIG, config)
                        }
                        return super.onStartCommand(tunnelIntent, flags, startId)
                    } else {
                        // Custom scanned profile (VLESS/Trojan/StormDNS). The
                        // disconnect must NOT stopSelf the service — a custom
                        // session starts immediately after and would be killed
                        // with it (the "connects then instantly disconnects" bug).
                        val stopTunnelIntent = Intent(this, AshaVpnService::class.java).apply {
                            action = ACTION_DISCONNECT
                            putExtra(EXTRA_KEEP_SERVICE, true)
                        }
                        super.onStartCommand(stopTunnelIntent, flags, startId)
                        startCustomVpn(rawConfig, cleanIp, profileName)
                        return START_NOT_STICKY
                    }
                }
                ACTION_STOP -> {
                    stopCustomVpn()
                    runCatching { android.system.Os.unsetenv("AETHER_ENROLL_ADDRESS") }
                    runCatching { android.system.Os.unsetenv("AETHER_ECH") }
                    runCatching { android.system.Os.unsetenv("AETHER_MASQUE_HTTP2") }
                    runCatching { android.system.Os.unsetenv("AETHER_MASQUE_H2_FRAGMENT") }
                    runCatching { android.system.Os.unsetenv("AETHER_API_FRAGMENT") }
                    runCatching { android.system.Os.unsetenv("AETHER_SOCKS_PROXY") }
                    runCatching { android.system.Os.unsetenv("AETHER_UPSTREAM") }
                    runCatching { android.system.Os.unsetenv("AETHER_PEER") }
                    runCatching { android.system.Os.unsetenv("AETHER_MASQUE_H2_PEER") }
                    runCatching { android.system.Os.unsetenv("AETHER_WG_PEER") }
                    runCatching { android.system.Os.unsetenv("AETHER_MASQUE_MTU") }
                    runCatching { android.system.Os.unsetenv("AETHER_WG_MTU") }
                    val stopTunnelIntent = Intent(this, AshaVpnService::class.java).apply {
                        action = ACTION_DISCONNECT
                    }
                    super.onStartCommand(stopTunnelIntent, flags, startId)
                    return START_NOT_STICKY
                }
                ACTION_CONNECT -> {
                    stopCustomVpn()
                    return super.onStartCommand(intent, flags, startId)
                }
                ACTION_DISCONNECT -> {
                    stopCustomVpn()
                    return super.onStartCommand(intent, flags, startId)
                }
                else -> {
                    return super.onStartCommand(intent, flags, startId)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("AshaVpnService", "Fatal error in onStartCommand", t)
            VpnManager.updateStats {
                it.copy(
                    status = VpnStatus.ERROR,
                    errorMessage = "Error: ${t.javaClass.simpleName}: ${t.message ?: "Unknown error"}",
                    detailMessage = t.stackTraceToString()
                )
            }
            return START_NOT_STICKY
        }
    }

    private fun startCustomVpn(rawConfig: String, cleanIp: String?, profileName: String) {
        isCustomActive = true
        isConnectedAndBridged = false
        customConnectedStartMs = System.currentTimeMillis()

        // Fresh steps: the store is global, so a previous PoW/chain session's
        // rows must not survive into a custom-profile connect.
        com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.reset()
        com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
            com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.STARTING,
            progressPercent = 10,
            detail = "Starting $profileName…",
            outerLabel = "Custom",
        )
        if (rawConfig.isBlank()) {
            stopCustomVpn()
            return
        }

        val proxy = runCatching { ProxyParser.parse(rawConfig) }.getOrNull()
        if (proxy == null) {
            VpnManager.updateStats {
                it.copy(status = VpnStatus.ERROR, errorMessage = "Failed to parse proxy configuration")
            }
            stopCustomVpn()
            return
        }

        sendStatus(STATUS_CONNECTING, "Connecting $profileName...")
        VpnManager.updateStats {
            it.copy(
                status = VpnStatus.CONNECTING,
                activeProfile = VpnProfile(
                    id = "active",
                    name = profileName,
                    raw = rawConfig,
                    proxy = proxy,
                    cleanIp = cleanIp,
                )
            )
        }

        customScope.launch {
            try {
                // If StormDNS or CottenDNS, start the native DNS tunnel client
                if (proxy.protocol == Protocol.STORMDNS) {
                    val isCotten = proxy.isCottenDns()
                    val engine = proxy.dnsEngine()
                    val engineName = if (isCotten) "CottenDNS" else "StormDNS"

                    val customText = runCatching { SettingsStore(applicationContext).customDnsResolvers.first() }.getOrDefault("")
                    val defaultLines = runCatching {
                        applicationContext.assets.open("default_resolvers.txt").bufferedReader().useLines { it.toList() }
                    }.getOrDefault(emptyList())
                    val curatedLines = runCatching {
                        applicationContext.assets.open("dns_resolvers.txt").bufferedReader().useLines { it.toList() }
                    }.getOrDefault(emptyList())
                    val allLines = defaultLines + curatedLines + customText.lines()
                    val resolverIps = allLines.mapNotNull { com.ahoura.asha_scanner_ip.core.dns.DnsProbe.parseResolverLine(it)?.ip }.distinct()

                    // The auto-tuned MTU profile — falls back to the balanced
                    // default when the stored id no longer matches a preset.
                    val tunePreset = runCatching {
                        SettingsStore(applicationContext).stormPresetId.first()
                    }.getOrDefault("iran-average")
                        .let { com.ahoura.asha_scanner_ip.core.storm.StormAutoTunePresets.byId(it) }

                    stormDnsManager.start(
                        domain = proxy.address,
                        encryptionKey = proxy.password,
                        encryptionMethod = proxy.encryption.toIntOrNull() ?: 1,
                        resolverIps = resolverIps,
                        engine = engine,
                        tune = tunePreset,
                    )
                    val ready = stormDnsManager.waitForPort(timeoutMillis = 35_000)
                    if (!ready) {
                        val reason = stormDnsManager.lastError ?: "failed to connect to resolvers (check server domain & key)"
                        throw IllegalStateException("$engineName: $reason")
                    }
                }

                val bypassIran = runCatching {
                    SettingsStore(applicationContext).vpnBypassIr.first()
                }.getOrDefault(true)
                val dnsIp = runCatching {
                    SettingsStore(applicationContext).vpnDnsIp.first().trim()
                }.getOrDefault("1.1.1.1").let { raw ->
                    if (raw.matches(Regex("[0-9.]+")) || raw.matches(Regex("[0-9a-fA-F:]+"))) raw else "1.1.1.1"
                }

                val socksPort = if (proxy.protocol == Protocol.AETHER) {
                    com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.SOCKS_PORT
                } else {
                    XrayProcessManager.DEFAULT_SOCKS_PORT
                }
                val httpPort = XrayProcessManager.DEFAULT_HTTP_PORT

                if (proxy.protocol == Protocol.AETHER) {
                    val aetherProto = proxy.encryption.ifBlank { "masque" }
                    val protoLabel = aetherProto.uppercase()

                    com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                        com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.STARTING,
                        progressPercent = 25,
                        detail = "Verifying Cloudflare credentials ($protoLabel)…",
                        outerLabel = protoLabel,
                    )

                    sendStatus(STATUS_CONNECTING, "Checking Cloudflare credentials…")

                    val keysReady = com.ahoura.asha_scanner_ip.core.aether.AetherKeyManager.ensureKeysFor(
                        applicationContext,
                        aetherProto,
                    ) { msg ->
                        sendStatus(STATUS_CONNECTING, msg)
                        VpnManager.updateStats { s -> s.copy(detailMessage = msg) }
                        com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                            com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.STARTING,
                            progressPercent = 40,
                            detail = msg,
                            outerLabel = protoLabel,
                        )
                    }
                    if (!keysReady) {
                        throw IllegalStateException("Failed to provision Cloudflare WARP keys")
                    }

                    val effectivePeer = if (!cleanIp.isNullOrBlank()) {
                        cleanIp
                    } else if (proxy.address.isNotBlank()) {
                        "${proxy.address}:${if (proxy.port > 0) proxy.port else 443}"
                    } else {
                        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                        val storedClean = prefs.getString("clean_ip", null)?.trim().orEmpty()
                        if (storedClean.isNotBlank() && CoreConfig.isWarpEndpoint(storedClean)) storedClean else ""
                    }

                    com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                        com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.OUTER,
                        progressPercent = 55,
                        detail = if (effectivePeer.isNotBlank()) "Connecting to clean endpoint: $effectivePeer" else "Scanning Cloudflare gateways ($protoLabel)…",
                        outerLabel = protoLabel,
                    )

                    val cliSettings = com.ahoura.asha_scanner_ip.core.aether.AetherCliSettings(
                        protocol = aetherProto,
                        socksPort = socksPort,
                        peer = effectivePeer,
                        scanMode = if (effectivePeer.isBlank()) proxy.path.ifBlank { "balanced" } else "",
                        masqueSni = proxy.sni.ifBlank { "www.cloudflare.com" },
                        fragment = proxy.raw.contains("fragment=1"),
                        quickReconnect = true,
                    )
                    val args = com.ahoura.asha_scanner_ip.core.aether.AetherCliConfig.buildArgs(cliSettings)

                    val started = com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.start(
                        applicationContext,
                        args,
                        socksPort,
                    ) { line ->
                        if (isConnectedAndBridged || !isCustomActive) return@start
                        val userMsg = when {
                            line.contains("selected") || line.contains("candidate ok") -> "Candidate gateway selected"
                            line.contains("connecting") || line.contains("probing") -> "Testing Cloudflare routes…"
                            line.contains("ready") || line.contains("connected") -> "Secure tunnel established"
                            else -> null
                        }
                        if (userMsg != null) {
                            VpnManager.updateStats { s -> s.copy(detailMessage = userMsg) }
                            com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                                com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.INNER,
                                progressPercent = 70,
                                detail = userMsg,
                                outerLabel = protoLabel,
                            )
                        }
                    }
                    if (!started) {
                        throw IllegalStateException(
                            com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.lastError
                                .ifBlank { "Aether engine failed to start" }
                        )
                    }
                } else {
                    val configJson = XrayConfigBuilder.buildClientVpnJson(
                        proxy = proxy,
                        candidateIp = cleanIp,
                        socksPort = socksPort,
                        httpPort = httpPort,
                        bypassIran = bypassIran,
                        dnsServers = listOf(dnsIp, "8.8.8.8"),
                        includeTun = false,
                    )

                    val started = XrayProcessManager.start(applicationContext, configJson, socksPort)
                    if (!started) {
                        throw IllegalStateException("Failed to start Xray core: ${XrayProcessManager.lastError}")
                    }
                }

                // Start UDP/SOCKS front-end to translate badvpn udpgw to SOCKS5 UDP ASSOCIATE
                runCatching { com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.stop() }
                val frontStarted = com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.start(
                    socksPort,
                    listOf(dnsIp, "8.8.8.8"),
                )
                if (!frontStarted) {
                    throw IllegalStateException("Failed to start UDP front-end on port ${com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.LISTEN_PORT}")
                }

                // Establish TUN interface
                val builder = Builder()
                    .setSession("Asha VPN - $profileName")
                    .setMtu(Tun2SocksManager.VPN_INTERFACE_MTU)

                val privateAddr = Tun2SocksManager.selectPrivateAddress()
                builder.addAddress(privateAddr.ipAddress, privateAddr.prefixLength)
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute(privateAddr.subnet, privateAddr.prefixLength)
                // lwIP resolver is the sole DNS server on the TUN interface:
                // badvpn intercepts queries to this router address with FLAG_DNS and
                // routes them through ShardSocksFront to the encrypted upstream SOCKS5
                // tunnel, preventing carrier DNS poisoning/blocking.
                builder.addDnsServer(privateAddr.router)
                SplitTunnelSettings(this@AshaVpnService).applyTo(builder, this@AshaVpnService)

                val pfd = builder.establish() ?: throw IllegalStateException("VPN Builder.establish() returned null")
                customVpnInterface = pfd

                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.BRIDGING,
                    progressPercent = 85,
                    detail = "Routing device traffic via tun2socks…",
                    outerLabel = profileName,
                )

                // Bridge TUN to ShardSocksFront.LISTEN_PORT (which relays TCP/UDP/DNS to socksPort)
                val bridged = Tun2SocksManager.start(
                    pfd,
                    com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.LISTEN_PORT,
                    mtu = Tun2SocksManager.SHARD_TUNNEL_MTU,
                )
                if (!bridged) {
                    val detail = Tun2SocksManager.lastError.ifBlank { "Failed to bridge TUN to SOCKS5 via tun2socks" }
                    throw IllegalStateException(detail)
                }

                // A stop that raced the establish must win, or tun2socks would
                // re-arm after stopCustomVpn() tore it down.
                if (!isCustomActive) {
                    runCatching { pfd.close() }
                    customVpnInterface = null
                    return@launch
                }
                customConnectedStartMs = System.currentTimeMillis()
                isConnectedAndBridged = true

                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.CONNECTED,
                    progressPercent = 100,
                    detail = "Connection established (secure tunnel active)",
                    outerLabel = profileName,
                )

                sendStatus(STATUS_CONNECTED, profileName)
                VpnManager.updateStats {
                    it.copy(
                        status = VpnStatus.CONNECTED,
                        connectedDurationSeconds = 0L,
                        errorMessage = null,
                        detailMessage = "Connected ✓",
                    )
                }

                startCustomTimer()
                if (proxy.protocol == Protocol.AETHER) {
                    startAetherPing()
                } else {
                    startCustomPing(proxy, cleanIp)
                }

            } catch (e: Exception) {
                val err = e.message ?: "Connection failed"
                sendStatus(STATUS_FAILED, err)
                VpnManager.updateStats {
                    it.copy(status = VpnStatus.ERROR, errorMessage = err, detailMessage = err)
                }
                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.FAILED,
                    progressPercent = 0,
                    detail = err,
                    outerLabel = "Error",
                )
                stopCustomVpn()
            }
        }
    }

    private fun startCustomTimer() {
        customTimerJob?.cancel()
        customTimerJob = customScope.launch {
            var lastRx = com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.sessionRx
            var lastTx = com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.sessionTx
            var lastTime = System.currentTimeMillis()

            while (isActive && isCustomActive) {
                delay(1000)
                val engineRunning = if (com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.isRunning) {
                    true
                } else {
                    XrayProcessManager.isRunning
                }
                if (!engineRunning || !Tun2SocksManager.isRunning) {
                    val err = if (com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.isRunning) {
                        com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.lastError.ifBlank { "Aether process exited unexpectedly" }
                    } else {
                        XrayProcessManager.lastError.ifBlank { "VPN engine process exited unexpectedly" }
                    }
                    sendStatus(STATUS_FAILED, err)
                    VpnManager.updateStats {
                        it.copy(status = VpnStatus.ERROR, errorMessage = err)
                    }
                    stopCustomVpn()
                    break
                }
                // Battery saving: throttle UI duration ticks when screen is off
                if (!isScreenInteractive()) {
                    delay(4000)
                }
                val now = System.currentTimeMillis()
                val duration = (now - customConnectedStartMs) / 1000

                val curRx = com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.sessionRx
                val curTx = com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.sessionTx
                val dt = ((now - lastTime) / 1000.0).coerceAtLeast(0.001)
                val rxBps = ((curRx - lastRx) / dt).toLong().coerceAtLeast(0L)
                val txBps = ((curTx - lastTx) / dt).toLong().coerceAtLeast(0L)
                lastRx = curRx
                lastTx = curTx
                lastTime = now

                VpnManager.updateStats {
                    if (it.status == VpnStatus.CONNECTED) {
                        it.copy(
                            connectedDurationSeconds = duration,
                            downloadBps = rxBps,
                            uploadBps = txBps,
                        )
                    } else it
                }
            }
        }
    }

    private fun startCustomPing(proxy: ProxyConfig, cleanIp: String?) {
        customPingJob?.cancel()
        customPingJob = customScope.launch {
            val target = cleanIp?.ifBlank { null } ?: proxy.address
            val port = proxy.port
            val sni = proxy.effectiveSni()
            while (isActive && isCustomActive) {
                // Battery saving: if screen is off, suspend ping measurement completely
                // to allow cellular modem / Wi-Fi to enter low-power sleep state.
                if (!isScreenInteractive()) {
                    delay(30_000)
                    continue
                }
                val ping = VpnManager.measurePingDirect(target, port, sni)
                VpnManager.updateStats {
                    if (it.status == VpnStatus.CONNECTED) {
                        it.copy(pingMs = ping)
                    } else it
                }
                delay(12_000) // 12s interval allows mobile radio tail state to drop to idle
            }
        }
    }

    private fun stopCustomVpn() {
        val wasRunningCustom = isCustomActive
        isCustomActive = false
        isConnectedAndBridged = false
        customTimerJob?.cancel()
        customPingJob?.cancel()
        customTimerJob = null
        customPingJob = null

        com.ahoura.asha_scanner_ip.core.guard.TunPacketFilter.stop()
        Tun2SocksManager.stop()
        runCatching { com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.stop() }
        XrayProcessManager.stop()
        runCatching { stormDnsManager.stop() }
        runCatching {
            com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.stop()
        }

        runCatching { customVpnInterface?.close() }
        customVpnInterface = null

        // Only a session this service was ACTUALLY running may report
        // DISCONNECTED. On a guard-transport connect this runs BEFORE the new
        // session starts — VpnManager.startTransport has just written
        // CONNECTING, and broadcasting DISCONNECTED here announced a teardown
        // of a tunnel that never existed, racing the new session's own status.
        if (!wasRunningCustom) return

        VpnManager.updateStats {
            // Preserve a failure reason: the catch paths set ERROR before calling
            // here, and overwriting it with DISCONNECTED hid why custom connect
            // died. Only a clean teardown reports DISCONNECTED.
            if (it.status != VpnStatus.ERROR) {
                sendStatus(STATUS_DISCONNECTED)
                it.copy(status = VpnStatus.DISCONNECTED, connectedDurationSeconds = 0L)
            } else {
                it.copy(connectedDurationSeconds = 0L)
            }
        }
    }

    /**
     * Aether v2.4.0 CLI transport: run the bundled v2.4.0 core as a SOCKS
     * process and bridge the device TUN through tun2socks pointed straight at
     * its listener — the same data path the WOM VPN mode proved. The v2.4.0
     * feature set (multi-domain WARP API with fallbacks, API ClientHello
     * fragmentation, MASQUE SNI override, Zero Trust cert fix, upstream
     * fail-fast, TLS fingerprint flags) lives entirely inside this process.
     * Identity lives in its own file so the CLI and the JNI cores never
     * clobber each other.
     */
    private fun startAetherCliVpn(profileName: String) {
        isCustomActive = true
        isConnectedAndBridged = false
        customConnectedStartMs = System.currentTimeMillis()

        com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.reset()
        com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
            com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.STARTING,
            progressPercent = 10,
            detail = "Starting Aether v2.4…",
            outerLabel = "Aether",
        )

        VpnManager.updateStats {
            it.copy(
                status = VpnStatus.CONNECTING,
                transport = "aether24",
                activeProfile = null,
                errorMessage = null,
                detailMessage = "Checking Cloudflare credentials…",
                progress = 10,
            )
        }

        customScope.launch {
            try {
                val settings = SettingsStore(applicationContext)
                val cliSettings = com.ahoura.asha_scanner_ip.core.aether.AetherCliConfig.fromJson(
                    json = runCatching { settings.aether24Json.first() }.getOrDefault("{}"),
                    identityFile = "",
                    socksPort = com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.SOCKS_PORT,
                )

                val protoLabel = cliSettings.protocol.uppercase()

                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.STARTING,
                    progressPercent = 25,
                    detail = "Verifying Cloudflare credentials ($protoLabel)…",
                    outerLabel = protoLabel,
                )

                sendStatus(STATUS_CONNECTING, "Checking Cloudflare credentials…")
                val keysReady = com.ahoura.asha_scanner_ip.core.aether.AetherKeyManager.ensureKeysFor(
                    applicationContext,
                    cliSettings.protocol,
                ) { msg ->
                    sendStatus(STATUS_CONNECTING, msg)
                    VpnManager.updateStats { s -> s.copy(detailMessage = msg) }
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                        com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.STARTING,
                        progressPercent = 40,
                        detail = msg,
                        outerLabel = protoLabel,
                    )
                }
                if (!keysReady) {
                    throw IllegalStateException("Failed to provision Cloudflare WARP keys")
                }

                // If no manual peer is set, inspect stored clean IP candidate
                val cleanCandidate = if (cliSettings.peer.isNotBlank()) {
                    cliSettings.peer
                } else {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    val storedClean = prefs.getString("clean_ip", null)?.trim().orEmpty()
                    if (storedClean.isNotBlank() && CoreConfig.isWarpEndpoint(storedClean)) storedClean else ""
                }

                val finalSettings = cliSettings.copy(
                    peer = cleanCandidate,
                    scanMode = if (cleanCandidate.isBlank()) "balanced" else "",
                    quickReconnect = true,
                )
                val args = com.ahoura.asha_scanner_ip.core.aether.AetherCliConfig.buildArgs(finalSettings)

                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.OUTER,
                    progressPercent = 55,
                    detail = if (cleanCandidate.isNotBlank()) "Connecting to clean endpoint: $cleanCandidate" else "Scanning Cloudflare gateways ($protoLabel)…",
                    outerLabel = protoLabel,
                )

                val started = com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.start(
                    applicationContext, args,
                    com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.SOCKS_PORT,
                ) { line ->
                    if (isConnectedAndBridged || !isCustomActive) return@start
                    val userMsg = when {
                        line.contains("selected") || line.contains("candidate ok") -> "Candidate gateway selected"
                        line.contains("connecting") || line.contains("probing") -> "Testing Cloudflare routes…"
                        line.contains("ready") || line.contains("connected") -> "Secure tunnel established"
                        else -> null
                    }
                    if (userMsg != null) {
                        VpnManager.updateStats { s -> s.copy(detailMessage = userMsg) }
                        com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                            com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.INNER,
                            progressPercent = 70,
                            detail = userMsg,
                            outerLabel = protoLabel,
                        )
                    }
                }
                if (!started) {
                    throw IllegalStateException(
                        com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.lastError
                            .ifBlank { "Aether engine failed to start" }
                    )
                }

                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.BRIDGING,
                    progressPercent = 85,
                    detail = "Routing device traffic via tun2socks…",
                    outerLabel = protoLabel,
                )

                val dnsIp = runCatching { settings.vpnDnsIp.first().trim() }.getOrDefault("1.1.1.1")
                    .let { raw -> if (raw.matches(Regex("[0-9.]+")) || raw.matches(Regex("[0-9a-fA-F:]+"))) raw else "1.1.1.1" }

                val aetherSocksPort = com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.SOCKS_PORT
                runCatching { com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.stop() }
                val frontStarted = com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.start(
                    aetherSocksPort,
                    listOf(dnsIp, "8.8.8.8"),
                )
                if (!frontStarted) {
                    throw IllegalStateException("Failed to start UDP front-end on port ${com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.LISTEN_PORT}")
                }

                val privateAddr = Tun2SocksManager.selectPrivateAddress()
                val builder = Builder()
                    .setSession("Asha VPN - $profileName")
                    .setMtu(Tun2SocksManager.VPN_INTERFACE_MTU)

                builder.addAddress(privateAddr.ipAddress, privateAddr.prefixLength)
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute(privateAddr.subnet, privateAddr.prefixLength)
                // lwIP resolver is the sole DNS server on the TUN interface:
                // badvpn intercepts queries to this router address with FLAG_DNS and
                // routes them through ShardSocksFront to the encrypted upstream SOCKS5
                // tunnel, preventing carrier DNS poisoning/blocking.
                builder.addDnsServer(privateAddr.router)
                SplitTunnelSettings(this@AshaVpnService).applyTo(builder, this@AshaVpnService)

                val pfd = builder.establish() ?: throw IllegalStateException("VPN Builder.establish() returned null")
                customVpnInterface = pfd

                val bridged = Tun2SocksManager.start(
                    pfd,
                    com.ahoura.asha_scanner_ip.core.guard.ShardSocksFront.LISTEN_PORT,
                    mtu = Tun2SocksManager.SHARD_TUNNEL_MTU,
                )
                if (!bridged) throw IllegalStateException("Failed to bridge TUN to SOCKS5 via tun2socks")

                if (!isCustomActive) {
                    runCatching { pfd.close() }
                    customVpnInterface = null
                    return@launch
                }
                customConnectedStartMs = System.currentTimeMillis()
                isConnectedAndBridged = true

                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.CONNECTED,
                    progressPercent = 100,
                    detail = "Connection established (secure tunnel active)",
                    outerLabel = protoLabel,
                )

                sendStatus(STATUS_CONNECTED, profileName)
                VpnManager.updateStats {
                    it.copy(
                        status = VpnStatus.CONNECTED,
                        connectedDurationSeconds = 0L,
                        errorMessage = null,
                        detailMessage = "Connected ✓",
                    )
                }
                startCustomTimer()
                startAetherPing()
            } catch (e: Exception) {
                val err = e.message ?: "Connection to Aether failed"
                sendStatus(STATUS_FAILED, err)
                VpnManager.updateStats {
                    it.copy(status = VpnStatus.ERROR, errorMessage = err, detailMessage = err)
                }
                com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore.update(
                    com.ahoura.asha_scanner_ip.core.guard.ConnectionPhase.FAILED,
                    progressPercent = 0,
                    detail = err,
                    outerLabel = "Error",
                )
                stopCustomVpn()
            }
        }
    }

    private fun startAetherPing() {
        customPingJob?.cancel()
        customPingJob = customScope.launch {
            while (isActive && isCustomActive) {
                if (!isScreenInteractive()) {
                    delay(30_000)
                    continue
                }
                val ep = com.ahoura.asha_scanner_ip.core.aether.AetherCliProcessManager.lastSelectedEndpoint
                val target = if (ep.isNotBlank()) ep.substringBefore(":") else "1.1.1.1"
                val port = if (ep.contains(":")) ep.substringAfter(":").toIntOrNull() ?: 443 else 443
                val ping = VpnManager.measurePingDirect(target, port, "www.cloudflare.com")
                VpnManager.updateStats {
                    if (it.status == VpnStatus.CONNECTED) {
                        it.copy(pingMs = ping)
                    } else it
                }
                delay(12_000)
            }
        }
    }

    override fun onDestroy() {
        stopCustomVpn()
        runCatching { android.system.Os.unsetenv("AETHER_ENROLL_ADDRESS") }
        runCatching { android.system.Os.unsetenv("AETHER_API_ADDRESS") }
        runCatching { android.system.Os.unsetenv("AETHER_ECH") }
        super.onDestroy()
    }
}
