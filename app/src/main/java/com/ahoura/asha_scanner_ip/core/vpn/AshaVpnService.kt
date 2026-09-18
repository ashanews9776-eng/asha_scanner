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

                    val transport = intent.getStringExtra(EXTRA_TRANSPORT)?.trim()?.lowercase() ?: "custom"
                    val rawConfig = intent.getStringExtra(EXTRA_RAW_CONFIG) ?: ""
                    val cleanIp = intent.getStringExtra(EXTRA_CLEAN_IP)
                    val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Proxy"

                    if (transport != "custom" && rawConfig.isBlank()) {
                        // Asha Guard transport: wireguard, masque, gool, psiphon, tor, shard
                        stopCustomVpn()
                        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                        prefs.edit().putString("default_protocol", transport).apply()
                        val armed = prefs.getBoolean("chain_armed", true)
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

                        val tunnelIntent = Intent(this, AshaVpnService::class.java).apply {
                            action = ACTION_CONNECT
                            val config = CoreConfig.json(this@AshaVpnService, effectiveProto)
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

                    stormDnsManager.start(
                        domain = proxy.address,
                        encryptionKey = proxy.password,
                        encryptionMethod = proxy.encryption.toIntOrNull() ?: 1,
                        resolverIps = resolverIps,
                        engine = engine,
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
        super.onDestroy()
    }
}
