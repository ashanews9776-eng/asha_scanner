package com.ahoura.asha_scanner_ip

import android.app.Application
import android.util.Log
import com.ahoura.asha_scanner_ip.core.vpn.VpnManager
import com.ahoura.asha_scanner_ip.core.vpn.VpnStatus
import java.io.File

class AshaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global uncaught exception handler to prevent silent crash and capture stacktrace
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("AshaApp", "FATAL UNCAUGHT EXCEPTION in thread [${thread.name}]", throwable)
                val crashFile = File(filesDir, "last_crash.txt")
                crashFile.writeText(
                    "Crash Time: ${java.util.Date()}\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n\n" +
                    "Stacktrace:\n${throwable.stackTraceToString()}"
                )
                VpnManager.updateStats {
                    it.copy(
                        status = VpnStatus.ERROR,
                        errorMessage = "Fatal: ${throwable.javaClass.simpleName}: ${throwable.message ?: "Unknown error"}",
                        detailMessage = throwable.stackTraceToString()
                    )
                }
            } catch (_: Throwable) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialize VpnManager safely
        runCatching {
            VpnManager.init(this)
        }.onFailure {
            Log.e("AshaApp", "Failed to init VpnManager on app launch", it)
        }

        // Surface the previous run's crash report in the VPN error card — the
        // only channel a release build has to tell us why connect crashed.
        runCatching {
            val crashFile = File(filesDir, "last_crash.txt")
            if (crashFile.exists() && crashFile.length() > 0L) {
                val report = crashFile.readText().take(4000)
                VpnManager.updateStats {
                    it.copy(
                        status = VpnStatus.ERROR,
                        errorMessage = "Last crash captured — see detail",
                        detailMessage = report,
                    )
                }
                crashFile.delete()
            }
        }
    }
}
