package com.ahoura.asha_scanner_ip.core.validator

import android.content.Context
import android.util.Log
import com.ahoura.asha_scanner_ip.core.guard.ConnectionLog
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the standalone native xray executable (libxray.so) for custom proxy profiles
 * (VLESS, Trojan, StormDNS, CottenDNS).
 */
object XrayProcessManager {

    private const val TAG = "XrayProcessManager"
    const val DEFAULT_SOCKS_PORT = 10808
    const val DEFAULT_HTTP_PORT = 10809

    private val running = AtomicBoolean(false)

    @Volatile
    private var process: Process? = null

    @Volatile
    private var logThread: Thread? = null

    @Volatile
    var lastError: String = ""
        private set

    /** Last lines the core printed — the actual reason it refuses a config. */
    private val recentOutput = ArrayDeque<String>()

    private fun noteOutput(line: String) {
        synchronized(recentOutput) {
            recentOutput.addLast(line)
            while (recentOutput.size > 8) recentOutput.removeFirst()
        }
    }

    private fun recentOutputText(): String =
        synchronized(recentOutput) { recentOutput.joinToString(" | ").take(400) }

    val isRunning: Boolean
        get() = running.get() && process?.isAlive == true

    private fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libxray.so")

    /**
     * Unpacks geoip.dat and geosite.dat into the files directory if needed.
     */
    fun unpackGeoAssets(context: Context) {
        val dir = context.filesDir
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val target = File(dir, name)
            val assetLen = runCatching { context.assets.open(name).use { it.available().toLong() } }.getOrDefault(0L)
            if (!target.exists() || target.length() == 0L || (assetLen > 0L && target.length() != assetLen)) {
                val tmp = File(dir, "$name.tmp")
                runCatching {
                    context.assets.open(name).use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmp.length() > 0L) {
                        if (target.exists()) target.delete()
                        tmp.renameTo(target)
                    } else {
                        tmp.delete()
                    }
                }
            }
        }
    }

    /**
     * Starts xray with the given JSON configuration.
     */
    @Synchronized
    fun start(context: Context, configJson: String, socksPort: Int = DEFAULT_SOCKS_PORT): Boolean {
        stop()
        unpackGeoAssets(context)

        val bin = binary(context)
        if (!bin.exists()) {
            lastError = "xray binary missing at ${bin.absolutePath}"
            Log.e(TAG, lastError)
            ConnectionLog.record("$TAG binary missing")
            return false
        }
        if (!bin.canExecute()) {
            runCatching { bin.setExecutable(true) }
        }

        val configFile = File(context.filesDir, "xray_custom.json")
        try {
            configFile.writeText(configJson)
        } catch (e: Exception) {
            lastError = "Failed to write config file: ${e.message}"
            Log.e(TAG, lastError, e)
            return false
        }

        val builder = ProcessBuilder(bin.absolutePath, "run", "-c", configFile.absolutePath)
        builder.directory(context.filesDir)
        builder.redirectErrorStream(true)
        builder.environment()["HOME"] = context.filesDir.absolutePath
        builder.environment()["XRAY_LOCATION_ASSET"] = context.filesDir.absolutePath

        val proc = try {
            builder.start()
        } catch (e: Exception) {
            lastError = "Could not start xray: ${e.message}"
            Log.e(TAG, lastError, e)
            return false
        }

        process = proc
        running.set(true)

        logThread = Thread({
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).forEachLine { line ->
                    if (line.isNotBlank() && !isNoise(line)) {
                        Log.d(TAG, line)
                        noteOutput(line)
                        ConnectionLog.record("$TAG $line")
                    }
                }
            } catch (_: Exception) {
            }
        }, "xray-log").apply { isDaemon = true }.also { it.start() }

        // Wait up to 3 seconds for the SOCKS port to accept
        val deadline = System.currentTimeMillis() + 3_000L
        var accepts = false
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                // The core printed why it refused the config — drain the log
                // thread and surface it, or the reason dies with the pipe.
                runCatching { logThread?.join(800) }
                lastError = "xray exited: ${recentOutputText()}"
                running.set(false)
                return false
            }
            if (portAccepts(socksPort, 200)) {
                accepts = true
                break
            }
            Thread.sleep(100)
        }

        if (!accepts) {
            runCatching { logThread?.join(800) }
            lastError = "xray SOCKS port $socksPort failed to respond: ${recentOutputText()}"
            stop()
            return false
        }

        return true
    }

    private fun isNoise(line: String): Boolean =
        line.contains("is deprecated, not recommended") ||
            line.contains("deprecated, will be removed soon") ||
            line.contains("Reading config")

    private fun portAccepts(port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    @Synchronized
    fun stop() {
        running.set(false)
        process?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
        process = null
        logThread = null
    }
}
