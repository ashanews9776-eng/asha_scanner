package com.ahoura.asha_scanner_ip.core.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.ahoura.asha_scanner_ip.core.net.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Singleton controller managing VPN state, connection intents, and ping measurement.
 */
object VpnManager {

    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    fun updateStats(transform: (VpnStats) -> VpnStats) {
        _stats.update(transform)
    }

    /**
     * Checks if VPN permission is required. Returns an Intent if permission dialog must be shown,
     * or null if already granted.
     */
    fun prepareVpn(activity: Activity): Intent? = VpnService.prepare(activity)

    /**
     * Starts the AshaVpnService with the given profile.
     */
    fun start(context: Context, profile: VpnProfile) {
        val intent = Intent(context, AshaVpnService::class.java).apply {
            action = AshaVpnService.ACTION_START
            putExtra(AshaVpnService.EXTRA_RAW_CONFIG, profile.raw)
            putExtra(AshaVpnService.EXTRA_CLEAN_IP, profile.cleanIp)
            putExtra(AshaVpnService.EXTRA_PROFILE_NAME, profile.name)
        }
        _stats.update {
            it.copy(status = VpnStatus.CONNECTING, activeProfile = profile, errorMessage = null)
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
        _stats.update { it.copy(status = VpnStatus.DISCONNECTING) }
        context.startService(intent)
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
