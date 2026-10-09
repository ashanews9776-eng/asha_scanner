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
import com.ahoura.asha_scanner_ip.core.guard.CoreConfig
import com.ahoura.asha_scanner_ip.core.guard.GuardVpnService
import com.ahoura.asha_scanner_ip.core.guard.MtuConfig
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

                    if (transport != "custom" && rawConfig.isBlank()) {
                        // Asha Guard transport: wireguard, masque, gool, psiphon, tor, shard
                        stopCustomVpn()
                        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                        prefs.edit().putString("default_protocol", transport).apply()

                        if (!cleanIp.isNullOrBlank()) {
                            prefs.edit().putString("clean_ip", cleanIp.trim()).apply()
                        }

                        val armed = if (explicitChained != null) {
                            if (transport == "psiphon") prefs.edit().putBoolean("chain_armed", explicitChained).apply()
                            if (transport == "tor") prefs.edit().putBoolean("tor_chain_armed", explicitChained).apply()
                            explicitChained
                        } else {
                            if (transport == "tor") TorManager.chainArmed(this) else prefs.getBoolean("chain_armed", false)
                        }

                        val effectiveProto = if (transport == "psiphon" && armed) {
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
                        val rawCleanIp = cleanIp?.trim()?.takeIf { it.isNotBlank() }
                            ?: manualEndpoint.takeIf { it.isNotBlank() }
                            ?: storedCleanIp.takeIf { it.isNotBlank() }

                        val masquePeer = CoreConfig.formatPeerForProtocol(rawCleanIp, "masque")
                        val wgPeer = CoreConfig.formatPeerForProtocol(rawCleanIp, "wireguard")
                        val chosenEnrollIp = if (effectiveProto.lowercase() in listOf("masque", "mim")) masquePeer else wgPeer

                        val enrollHost = rawCleanIp?.substringBefore(":")?.takeIf { it.isNotBlank() } ?: "162.159.192.1"
                        val enrollAddress = "$enrollHost:443"
                        runCatching { android.system.Os.setenv("AETHER_ENROLL_ADDRESS", enrollAddress, true) }

                        val echEnabled = prefs.getBoolean("ech_enabled", false)
                        if (echEnabled) {
                            runCatching { android.system.Os.setenv("AETHER_ECH", "auto", true) }
                        } else {
                            runCatching { android.system.Os.unsetenv("AETHER_ECH") }
                        }

                        val isChained = (transport == "psiphon" && armed) ||
                            (transport == "tor" && TorManager.chainArmed(this)) ||
                            effectiveProto.contains("chain") ||
                            effectiveProto.contains("psiphon-over-warp")
                        val isOuterWarp = effectiveProto.lowercase() in listOf("masque", "mim", "gool", "warp-in-warp") || isChained

                        if (isOuterWarp) {
                            runCatching {
                                val masqueTransport = prefs.getString("default_masque_transport", "h3")?.lowercase() ?: "h3"
                                if (masqueTransport == "h2" || isChained) {
                                    android.system.Os.setenv("AETHER_MASQUE_HTTP2", "1", true)
                                } else {
                                    android.system.Os.unsetenv("AETHER_MASQUE_HTTP2")
                                }
                                android.system.Os.setenv("AETHER_MASQUE_H2_FRAGMENT", "1", true)
                                android.system.Os.setenv("AETHER_API_FRAGMENT", "1", true)
                                android.system.Os.setenv("AETHER_MASQUE_STARTUP_SECS", "35", true)
                                android.system.Os.setenv("AETHER_MASQUE_VALIDATE_SECS", "15", true)
                                android.system.Os.setenv("AETHER_WG_VALIDATE_SECS", "15", true)
                                if (masquePeer != null || wgPeer != null) {
                                    if (masquePeer != null) {
                                        android.system.Os.setenv("AETHER_MASQUE_H2_PEER", masquePeer, true)
                                        android.system.Os.setenv("AETHER_PEER", masquePeer, true)
                                    }
                                    if (wgPeer != null) {
                                        android.system.Os.setenv("AETHER_WG_PEER", wgPeer, true)
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

                val socksPort = XrayProcessManager.DEFAULT_SOCKS_PORT
                val httpPort = XrayProcessManager.DEFAULT_HTTP_PORT
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

                // Establish TUN interface
                val builder = Builder()
                    .setSession("Asha VPN - $profileName")
                    .setMtu(1500)

                val privateAddr = Tun2SocksManager.selectPrivateAddress()
                builder.addAddress(privateAddr.ipAddress, privateAddr.prefixLength)
                builder.addDnsServer(dnsIp)
                builder.addDnsServer("8.8.8.8")
                builder.addRoute("0.0.0.0", 0)
                builder.setBlocking(false)
                runCatching { builder.addDisallowedApplication(packageName) }

                val pfd = builder.establish() ?: throw IllegalStateException("VPN Builder.establish() returned null")
                customVpnInterface = pfd

                // Bridge TUN to SOCKS5 via badvpn tun2socks
                val bridged = Tun2SocksManager.start(pfd, socksPort)
                if (!bridged) {
                    throw IllegalStateException("Failed to bridge TUN to SOCKS5 via tun2socks")
                }

                isCustomActive = true
                customConnectedStartMs = System.currentTimeMillis()
                sendStatus(STATUS_CONNECTED, profileName)
                VpnManager.updateStats {
                    it.copy(
                        status = VpnStatus.CONNECTED,
                        connectedDurationSeconds = 0L,
                        errorMessage = null,
                    )
                }

                startCustomTimer()
                startCustomPing(proxy, cleanIp)

            } catch (e: Exception) {
                val err = e.message ?: "Connection failed"
                sendStatus(STATUS_FAILED, err)
                VpnManager.updateStats {
                    it.copy(status = VpnStatus.ERROR, errorMessage = err)
                }
                stopCustomVpn()
            }
        }
    }

    private fun startCustomTimer() {
        customTimerJob?.cancel()
        customTimerJob = customScope.launch {
            while (isActive && isCustomActive) {
                delay(1000)
                if (!XrayProcessManager.isRunning || !Tun2SocksManager.isRunning) {
                    val err = XrayProcessManager.lastError.ifBlank { "VPN engine process exited unexpectedly" }
                    sendStatus(STATUS_FAILED, err)
                    VpnManager.updateStats {
                        it.copy(status = VpnStatus.ERROR, errorMessage = err)
                    }
                    stopCustomVpn()
                    break
                }
                val duration = (System.currentTimeMillis() - customConnectedStartMs) / 1000
                VpnManager.updateStats {
                    if (it.status == VpnStatus.CONNECTED) {
                        it.copy(connectedDurationSeconds = duration)
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
                val ping = VpnManager.measurePingDirect(target, port, sni)
                VpnManager.updateStats {
                    if (it.status == VpnStatus.CONNECTED) {
                        it.copy(pingMs = ping)
                    } else it
                }
                delay(5000)
            }
        }
    }

    private fun stopCustomVpn() {
        val wasRunningCustom = isCustomActive
        isCustomActive = false
        customTimerJob?.cancel()
        customPingJob?.cancel()
        customTimerJob = null
        customPingJob = null

        Tun2SocksManager.stop()
        XrayProcessManager.stop()
        runCatching { stormDnsManager.stop() }

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

    override fun onDestroy() {
        stopCustomVpn()
        runCatching { android.system.Os.unsetenv("AETHER_ENROLL_ADDRESS") }
        runCatching { android.system.Os.unsetenv("AETHER_ECH") }
        super.onDestroy()
    }
}
