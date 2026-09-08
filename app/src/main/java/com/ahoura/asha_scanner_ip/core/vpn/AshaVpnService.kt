package com.ahoura.asha_scanner_ip.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.ahoura.asha_scanner_ip.MainActivity
import com.ahoura.asha_scanner_ip.R
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.net.Tls
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import com.ahoura.asha_scanner_ip.core.validator.XrayConfigBuilder
import com.ahoura.asha_scanner_ip.data.SettingsStore
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Socket

class AshaVpnService : VpnService(), CoreCallbackHandler {

    companion object {
        const val ACTION_START = "com.ahoura.asha_scanner_ip.vpn.START"
        const val ACTION_STOP = "com.ahoura.asha_scanner_ip.vpn.STOP"
        const val EXTRA_RAW_CONFIG = "extra_raw_config"
        const val EXTRA_CLEAN_IP = "extra_clean_ip"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "asha_vpn_channel"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var timerJob: Job? = null
    private var pingJob: Job? = null
    private var connectedStartMs: Long = 0L

    @Volatile
    private var isStopping = false

    override fun onEmitStatus(status: Long, msg: String?): Long {
        // AndroidLibXrayLite only routes two informational lifecycle notes
        // through this hook ("Started successfully, running" / "Core stopped");
        // the state transitions arrive via startup()/shutdown() and start
        // failures throw from startLoop(). Nothing to do here.
        return 0L
    }

    override fun shutdown(): Long {
        // The library fires this whenever the core instance stops. If WE did not
        // initiate the stop (isStopping still false), the core died unexpectedly
        // — surface it instead of leaving the UI stuck on CONNECTED, then tear
        // the TUN/service down. Our own stopVpn() flips isStopping first, so the
        // shutdown() fired by our stopLoop() lands here as a no-op.
        if (!isStopping) {
            VpnManager.updateStats {
                it.copy(status = VpnStatus.ERROR, errorMessage = "Core stopped unexpectedly")
            }
            stopVpn()
        }
        return 0L
    }

    override fun startup(): Long {
        // Authoritative "core is running" signal from the library (called inside
        // startLoop once the instance started); corroborate the CONNECTED state
        // that startVpn sets right after startLoop returns.
        VpnManager.updateStats {
            if (it.status == VpnStatus.CONNECTING) {
                it.copy(status = VpnStatus.CONNECTED, connectedDurationSeconds = 0L, errorMessage = null)
            } else it
        }
        return 0L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // startForegroundService() obligates this service to reach
                // startForeground() within seconds — including on the error
                // paths inside startVpn (blank/invalid config) that stop the
                // service without connecting. Promote to foreground first, or
                // Android 12+ throws ForegroundServiceDidNotStartInTimeException.
                val name = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Proxy"
                startForeground(NOTIFICATION_ID, buildNotification("Connecting to $name...", isConnected = false))
                startVpn(
                    rawConfig = intent.getStringExtra(EXTRA_RAW_CONFIG) ?: "",
                    cleanIp = intent.getStringExtra(EXTRA_CLEAN_IP),
                    profileName = name,
                )
            }
            ACTION_STOP -> {
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(rawConfig: String, cleanIp: String?, profileName: String) {
        isStopping = false
        if (rawConfig.isBlank()) {
            stopVpn()
            return
        }

        val proxy = runCatching { ProxyParser.parse(rawConfig) }.getOrNull()
        if (proxy == null) {
            VpnManager.updateStats {
                it.copy(status = VpnStatus.ERROR, errorMessage = "Failed to parse proxy configuration")
            }
            stopVpn()
            return
        }

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
        // Foreground promotion already happened in onStartCommand.

        scope.launch {
            try {
                // Unpack the bundled geo assets (needed when the bypass-Iran
                // routing rules reference geosite:ir / geoip:ir).
                unpackGeoAssets(applicationContext)

                // Clear any bad xudp.basekey from previous process run
                runCatching { android.system.Os.unsetenv("xray.xudp.basekey") }

                // Initialize core environment with asset path; pass empty string for xudpBaseKey
                val filesPath = applicationContext.filesDir.absolutePath
                Libv2ray.initCoreEnv(filesPath, "")
                coreController = Libv2ray.newCoreController(this@AshaVpnService)

                // Build client JSON config
                val bypassIran = runCatching {
                    SettingsStore(applicationContext).vpnBypassIr.first()
                }.getOrDefault(true)
                val configJson = XrayConfigBuilder.buildClientVpnJson(proxy, cleanIp, bypassIran = bypassIran)

                // Establish TUN interface
                val builder = Builder()
                    .setSession("Asha VPN - $profileName")
                    .setMtu(1500)
                    .addAddress("172.19.0.1", 30)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)

                // Disallow self package to prevent routing loop for Xray outbound sockets
                runCatching {
                    builder.addDisallowedApplication(packageName)
                }

                runCatching {
                    builder.addAddress("fdfe:dcba:9876::1", 126)
                    builder.addRoute("::", 0)
                }

                vpnInterface = builder.establish()
                val fd = vpnInterface?.fd ?: throw IllegalStateException("Failed to establish VPN TUN interface")

                // Start Xray-core with the TUN interface file descriptor
                coreController?.startLoop(configJson, fd)

                connectedStartMs = System.currentTimeMillis()
                VpnManager.updateStats {
                    it.copy(status = VpnStatus.CONNECTED, connectedDurationSeconds = 0L, errorMessage = null)
                }
                updateNotification("Connected: $profileName", isConnected = true)

                val target = cleanIp?.ifBlank { null } ?: proxy.address
                startTimer()
                startPeriodicPing(proxy, target)

            } catch (e: Exception) {
                VpnManager.updateStats {
                    it.copy(status = VpnStatus.ERROR, errorMessage = e.message ?: "Connection failed")
                }
                stopVpn()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val duration = (System.currentTimeMillis() - connectedStartMs) / 1000
                // queryAllOutboundTrafficStats drains (resets) the core's
                // per-outbound counters, so each read is the last second's
                // bytes — a live up/down rate. Summed across all outbounds
                // (proxy + direct) so bypassed Iranian traffic counts too.
                val raw = runCatching { coreController?.queryAllOutboundTrafficStats() }.getOrNull().orEmpty()
                var upBps = 0L
                var downBps = 0L
                for (entry in raw.split(';')) {
                    val parts = entry.split(',')
                    if (parts.size != 3) continue
                    val v = parts[2].toLongOrNull() ?: continue
                    when (parts[1]) {
                        "uplink" -> upBps += v
                        "downlink" -> downBps += v
                    }
                }
                VpnManager.updateStats {
                    it.copy(connectedDurationSeconds = duration, uploadBps = upBps, downloadBps = downBps)
                }
            }
        }
    }

    private fun startPeriodicPing(proxy: ProxyConfig, target: String) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                val ping = VpnManager.measurePingDirect(target, proxy.port, proxy.effectiveSni())
                VpnManager.updateStats { it.copy(pingMs = ping) }
                delay(15000)
            }
        }
    }

    private fun stopVpn() {
        if (isStopping) return
        isStopping = true

        timerJob?.cancel()
        pingJob?.cancel()
        timerJob = null
        pingJob = null

        VpnManager.updateStats {
            it.copy(
                status = if (it.status == VpnStatus.ERROR) VpnStatus.ERROR else VpnStatus.DISCONNECTED,
                connectedDurationSeconds = 0L,
                pingMs = null,
                uploadBps = 0L,
                downloadBps = 0L,
            )
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        scope.launch(Dispatchers.IO) {
            try {
                coreController?.stopLoop()
            } catch (_: Exception) {
            } finally {
                coreController = null
            }

            try {
                Thread.sleep(100)
            } catch (_: Exception) {}

            try {
                vpnInterface?.close()
            } catch (_: Exception) {
            } finally {
                vpnInterface = null
                isStopping = false
            }
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asha VPN Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows live connection status for Asha VPN"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, isConnected: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AshaVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Asha VPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.app_icon_asha)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                if (isConnected) "Disconnect" else "Cancel",
                stopPending
            )
            .build()
    }

    private fun updateNotification(text: String, isConnected: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text, isConnected))
    }

    private fun unpackGeoAssets(context: Context) {
        // Only the trimmed Iran-focused databases we bundle (the full public
        // lists are ~27MB — our assets carry just the "IR" entries, ~40KB).
        // ALWAYS overwrite: an older install may have left a stale or
        // truncated geo file in filesDir, and xray fails with a misleading
        // "... EOF" while parsing it. Copy is atomic (tmp + rename) so a
        // killed process can never leave a half-written database behind.
        val datFiles = listOf("geoip.dat", "geosite.dat")
        for (name in datFiles) {
            runCatching {
                val tmp = java.io.File(context.filesDir, "$name.tmp")
                context.assets.open(name).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (tmp.length() > 0L) {
                    val target = java.io.File(context.filesDir, name)
                    if (target.exists() && !target.delete()) {
                        tmp.delete()
                        return@runCatching
                    }
                    if (!tmp.renameTo(target)) tmp.delete()
                } else {
                    tmp.delete()
                }
            }
        }
    }
}
