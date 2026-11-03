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
 * libxray.so.
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

    @Volatile
    var lastSelectedEndpoint: String = ""
        private set

    /** Last log lines the CLI printed — the reason it refused a flag/config. */
    private val recentOutput = ArrayDeque<String>()

    // Regex matchers for endpoint auto-discovery from Aether's logs
    private val masqueGatewayRegex = Regex("""selected MASQUE gateway (\S+)""")
    private val wireguardEndpointRegex = Regex("""selected WireGuard endpoint (\S+)""")
    private val goolHopsRegex = Regex("""using cloudflare edge (\S+) \(outer\) and (\S+) \(inner\)""")
    private val mimHopsRegex = Regex("""masque-in-masque ready: (\S+) \(outer\) and (\S+) \(inner\)""")
    private val goolOverMasqueRegex = Regex("""gool ready: masque (\S+) carries wireguard (\S+)""")

    private fun noteOutput(line: String) {
        synchronized(recentOutput) {
            recentOutput.addLast(line)
            while (recentOutput.size > 12) recentOutput.removeFirst()
        }
    }

    private fun recentOutputText(): String =
        synchronized(recentOutput) { recentOutput.joinToString(" | ").take(400) }

    val isRunning: Boolean
        get() = running.get() && process?.isAlive == true

    private fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    @Synchronized
    fun start(
        context: Context,
        args: List<String>,
        socksPort: Int = SOCKS_PORT,
        onOutput: ((String) -> Unit)? = null,
    ): Boolean {
        stop()

        val bin = binary(context)
        if (!bin.exists()) {
            lastError = "Aether v2.4 binary missing at ${bin.absolutePath}"
            Log.e(TAG, lastError)
            ConnectionLog.record("$TAG binary missing")
            return false
        }
        if (!bin.canExecute()) {
            lastError = "Aether v2.4 binary is not executable"
            Log.e(TAG, lastError)
            return false
        }

        synchronized(recentOutput) { recentOutput.clear() }
        lastSelectedEndpoint = ""

        val effectiveArgs = if (args.none { it == "-4" || it == "-6" || it == "--ip" }) {
            args + "-4"
        } else {
            args
        }

        val builder = ProcessBuilder(listOf(bin.absolutePath) + effectiveArgs)
        builder.directory(context.filesDir)
        builder.redirectErrorStream(true)
        builder.environment().apply {
            put("HOME", context.filesDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("AETHER_CONFIG", File(context.filesDir, "aether.toml").absolutePath)
            put("AETHER_MASQUE_CONFIG", File(context.filesDir, "aether-masque.toml").absolutePath)
            put("AETHER_WG_CONFIG", File(context.filesDir, "aether.toml").absolutePath)
            val certDirs = listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")
                .filter { File(it).isDirectory }
            if (certDirs.isNotEmpty()) {
                put("SSL_CERT_DIR", certDirs.joinToString(":"))
            }
        }

        val proc = try {
            builder.start().apply {
                // Immediately close stdin so CLI process NEVER blocks on interactive prompt
                runCatching { outputStream.close() }
            }
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
                        onOutput?.invoke(line)

                        // Check for selected endpoints
                        when {
                            masqueGatewayRegex.containsMatchIn(line) -> {
                                masqueGatewayRegex.find(line)?.groupValues?.getOrNull(1)?.let {
                                    lastSelectedEndpoint = it
                                }
                            }
                            wireguardEndpointRegex.containsMatchIn(line) -> {
                                wireguardEndpointRegex.find(line)?.groupValues?.getOrNull(1)?.let {
                                    lastSelectedEndpoint = it
                                }
                            }
                            goolOverMasqueRegex.containsMatchIn(line) -> {
                                goolOverMasqueRegex.find(line)?.groupValues?.getOrNull(1)?.let {
                                    lastSelectedEndpoint = it
                                }
                            }
                            goolHopsRegex.containsMatchIn(line) -> {
                                goolHopsRegex.find(line)?.groupValues?.getOrNull(1)?.let {
                                    lastSelectedEndpoint = it
                                }
                            }
                            mimHopsRegex.containsMatchIn(line) -> {
                                mimHopsRegex.find(line)?.groupValues?.getOrNull(1)?.let {
                                    lastSelectedEndpoint = it
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }, "aether24-log").apply { isDaemon = true }.also { it.start() }

        // Wait for the SOCKS listener; a bound port means the tunnel is ready
        val deadline = System.currentTimeMillis() + 35_000L
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
