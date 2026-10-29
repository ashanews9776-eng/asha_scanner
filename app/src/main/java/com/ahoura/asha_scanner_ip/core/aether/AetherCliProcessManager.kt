package com.ahoura.asha_scanner_ip.core.aether

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
 * Runs the bundled Aether v2.4.0 CLI (`libaether_v24.so`) as a standalone
 * SOCKS process — the same exec-pattern the app already proved with
 * libxray.so. The v2.4.0 core brings the new multi-domain WARP API
 * (api.devices.cloudflare.com with fallbacks), API ClientHello fragmentation,
 * the www.cloudflare.com MASQUE SNI default, Zero Trust certificate fixes and
 * the upstream fail-fast — none of which the older JNI core understands.
 */
object AetherCliProcessManager {

    private const val TAG = "AetherCliProcess"
    const val SOCKS_PORT = 10822
    private const val BINARY_NAME = "libaether_v24.so"

    private val running = AtomicBoolean(false)

    @Volatile
    private var process: Process? = null

    @Volatile
    private var logThread: Thread? = null

    @Volatile
    var lastError: String = ""
        private set

    /** Last log lines the CLI printed — the reason it refused a flag/config. */
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
        File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    @Synchronized
    fun start(context: Context, args: List<String>, socksPort: Int = SOCKS_PORT): Boolean {
        stop()

        val bin = binary(context)
        if (!bin.exists()) {
            lastError = "Aether v2.4 binary missing at ${bin.absolutePath}"
            Log.e(TAG, lastError)
            ConnectionLog.record("$TAG binary missing")
            return false
        }
        if (!bin.canExecute()) {
            lastError = "Aether v2.4 binary is not executable (extraction disabled?)"
            Log.e(TAG, lastError)
            return false
        }

        synchronized(recentOutput) { recentOutput.clear() }

        val builder = ProcessBuilder(listOf(bin.absolutePath) + args)
        builder.directory(context.filesDir)
        builder.redirectErrorStream(true)
        builder.environment()["HOME"] = context.filesDir.absolutePath

        val proc = try {
            builder.start()
        } catch (e: Exception) {
            lastError = "Could not start Aether v2.4: ${e.message}"
            Log.e(TAG, lastError, e)
            return false
        }

        process = proc
        running.set(true)

        logThread = Thread({
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).forEachLine { line ->
                    if (line.isNotBlank()) {
                        Log.d(TAG, line)
                        noteOutput(line)
                        ConnectionLog.record("$TAG $line")
                    }
                }
            } catch (_: Exception) {
            }
        }, "aether24-log").apply { isDaemon = true }.also { it.start() }

        // Wait for the SOCKS listener; the CLI binds held ports at startup
        // (v2.4.0 behaviour), so a bound port means the tunnel is coming up.
        val deadline = System.currentTimeMillis() + 20_000L
        var accepts = false
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                runCatching { logThread?.join(800) }
                lastError = "Aether v2.4 exited: ${recentOutputText()}"
                running.set(false)
                return false
            }
            if (portAccepts(socksPort, 250)) {
                accepts = true
                break
            }
            Thread.sleep(150)
        }

        if (!accepts) {
            runCatching { logThread?.join(800) }
            lastError = "Aether v2.4 SOCKS port $socksPort failed to respond: ${recentOutputText()}"
            stop()
            return false
        }

        return true
    }

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
                if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
        process = null
        logThread = null
    }
}
