package com.ahoura.asha_scanner_ip.core.vpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ahoura.asha_scanner_ip.core.net.Tls
import com.ahoura.asha_scanner_ip.core.guard.CoreConfig
import com.ahoura.asha_scanner_ip.core.guard.GuardVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Singleton controller managing VPN state, connection intents, ping measurement,
 * and status synchronization with [GuardVpnService] / [AshaVpnService].
 */
object VpnManager {

    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var durationJob: Job? = null
    private var connectedSinceMs: Long = 0L
    private var receiverRegistered = false

    const val ACTION_STATUS = "com.ahoura.asha_scanner_ip.core.guard.STATUS"

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return
            if (action == ACTION_STATUS) {
                val statusStr = intent.getStringExtra(GuardVpnService.EXTRA_STATUS)
                val detail = intent.getStringExtra(GuardVpnService.EXTRA_DETAIL)
                val progress = intent.getIntExtra(GuardVpnService.EXTRA_PROGRESS, -1)
                val exitIp = intent.getStringExtra(GuardVpnService.EXTRA_EXIT_IP)
                val country = intent.getStringExtra(GuardVpnService.EXTRA_NOTIFICATION_COUNTRY)
                val pingStr = intent.getStringExtra(GuardVpnService.EXTRA_NOTIFICATION_PING)
                val speedTx = intent.getLongExtra(GuardVpnService.EXTRA_TRAFFIC_SPEED_TX, -1L)
                val speedRx = intent.getLongExtra(GuardVpnService.EXTRA_TRAFFIC_SPEED_RX, -1L)

                _stats.update { current ->
                    var newStatus = current.status
                    var newError = current.errorMessage
                    var newDetail = detail ?: current.detailMessage

                    if (statusStr != null) {
                        when (statusStr) {
                            GuardVpnService.STATUS_CONNECTED -> {
                                newStatus = VpnStatus.CONNECTED
                                newError = null
                                // Don't carry the stale "Connecting…" text into
                                // the connected state — clear it unless the
                                // service sent an explicit connected detail.
                                newDetail = detail
                                if (connectedSinceMs == 0L) {
                                    connectedSinceMs = System.currentTimeMillis()
                                    startDurationTicker()
                                }
                            }
                            GuardVpnService.STATUS_CONNECTING,
                            GuardVpnService.STATUS_STARTING,
                            GuardVpnService.STATUS_SCANNING -> {
                                newStatus = VpnStatus.CONNECTING
                                newError = null
                            }
                            GuardVpnService.STATUS_DISCONNECTED -> {
                                newStatus = VpnStatus.DISCONNECTED
                                connectedSinceMs = 0L
                                stopDurationTicker()
                            }
                            GuardVpnService.STATUS_FAILED -> {
                                newStatus = VpnStatus.ERROR
                                newError = detail ?: "Connection failed"
                                connectedSinceMs = 0L
                                stopDurationTicker()
                            }
                        }
                    }

                    val newExitIp = exitIp ?: current.exitIp
                    val newCountry = country ?: current.country
                    val pingMsVal = pingStr?.filter { it.isDigit() }?.toLongOrNull() ?: current.pingMs
                    val tx = if (speedTx >= 0) speedTx else current.uploadBps
                    val rx = if (speedRx >= 0) speedRx else current.downloadBps

                    current.copy(
                        status = newStatus,
                        detailMessage = newDetail,
                        progress = if (progress >= 0) progress else current.progress,
                        errorMessage = newError,
                        exitIp = newExitIp,
                        country = newCountry,
                        pingMs = pingMsVal,
                        uploadBps = tx,
                        downloadBps = rx,
                    )
                }
            }
        }
    }

    /**
     * Initializes the status receiver for background and tile events.
     */
    fun init(context: Context) {
        if (receiverRegistered) return
        runCatching {
            val filter = IntentFilter(ACTION_STATUS)
            ContextCompat.registerReceiver(
                context.applicationContext,
                statusReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }.onFailure {
            android.util.Log.e("VpnManager", "Failed to register statusReceiver", it)
        }
    }

    fun updateStats(transform: (VpnStats) -> VpnStats) {
        _stats.update(transform)
    }

    /**
     * Checks if VPN permission is required. Returns an Intent if permission dialog must be shown,
     * or null if already granted or if running in SOCKS-only proxy mode.
     */
    fun prepareVpn(activity: Activity): Intent? {
        if (CoreConfig.proxyOnly(activity)) return null
        return VpnService.prepare(activity)
    }

    /**
     * Starts an Asha Guard transport (wireguard, masque, gool, psiphon, tor, shard).
     */
    fun startTransport(context: Context, transport: String) {
        init(context)
        val intent = Intent(context, AshaVpnService::class.java).apply {
            action = AshaVpnService.ACTION_START
            putExtra(AshaVpnService.EXTRA_TRANSPORT, transport)
        }
        _stats.update {
            it.copy(
                status = VpnStatus.CONNECTING,
                transport = transport,
                activeProfile = null,
                errorMessage = null,
                detailMessage = "Starting $transport...",
                progress = -1,
                tunnelMode = CoreConfig.tunnelMode(context),
                localLanIp = CoreConfig.localNetworkAddress(context),
            )
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Starts the AshaVpnService with a custom proxy profile.
     */
    fun start(context: Context, profile: VpnProfile) {
        init(context)
        val intent = Intent(context, AshaVpnService::class.java).apply {
            action = AshaVpnService.ACTION_START
            putExtra(AshaVpnService.EXTRA_TRANSPORT, "custom")
            putExtra(AshaVpnService.EXTRA_RAW_CONFIG, profile.raw)
            putExtra(AshaVpnService.EXTRA_CLEAN_IP, profile.cleanIp)
            putExtra(AshaVpnService.EXTRA_PROFILE_NAME, profile.name)
        }
        _stats.update {
            it.copy(
                status = VpnStatus.CONNECTING,
                transport = "custom",
                activeProfile = profile,
                errorMessage = null,
                detailMessage = "Connecting ${profile.name}...",
                progress = -1,
                tunnelMode = CoreConfig.tunnelMode(context),
                localLanIp = CoreConfig.localNetworkAddress(context),
            )
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Stops the running VPN service.
     */
    fun stop(context: Context) {
        val intent = Intent(context, AshaVpnService::class.java).apply {
            action = AshaVpnService.ACTION_STOP
        }
        _stats.update { it.copy(status = VpnStatus.DISCONNECTING, detailMessage = "Disconnecting...") }
        context.startService(intent)
        stopDurationTicker()
    }

    private fun startDurationTicker() {
        durationJob?.cancel()
        durationJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (connectedSinceMs > 0L) {
                    val sec = (System.currentTimeMillis() - connectedSinceMs) / 1000
                    _stats.update {
                        if (it.status == VpnStatus.CONNECTED) it.copy(connectedDurationSeconds = sec)
                        else it
                    }
                }
            }
        }
    }

    private fun stopDurationTicker() {
        durationJob?.cancel()
        durationJob = null
        connectedSinceMs = 0L
    }

    /**
     * Measures TCP + TLS handshake delay in ms for a given profile.
     */
    suspend fun measurePing(profile: VpnProfile): Long? = withContext(Dispatchers.IO) {
        val target = profile.targetAddress
        val port = profile.proxy.port
        val sni = profile.proxy.effectiveSni()
        measurePingDirect(target, port, sni)
    }

    /**
     * Measures direct TCP/TLS connect latency to an endpoint.
     */
    suspend fun measurePingDirect(target: String, port: Int, sni: String): Long? = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        var socket: Socket? = null
        var ssl: SSLSocket? = null
        try {
            socket = Tls.dial(target, port, 4000)
            if (sni.isNotBlank()) {
                ssl = Tls.handshake(socket, target, port, sni, emptyList(), insecure = true, 4000)
            }
            val elapsed = (System.nanoTime() - start) / 1_000_000
            elapsed.coerceAtLeast(1)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { ssl?.close() }
            runCatching { socket?.close() }
        }
    }
}
