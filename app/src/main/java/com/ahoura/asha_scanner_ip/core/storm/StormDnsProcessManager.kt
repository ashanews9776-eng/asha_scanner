package com.ahoura.asha_scanner_ip.core.storm

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Manages the background execution of the native StormDNS client (`libstormdns_client.so`).
 *
 * Spawns a local SOCKS5 proxy on `127.0.0.1:10853` and tunnels TCP/UDP traffic
 * as DNS queries over UDP port 53 via the provided list of DNS resolvers.
 */
class StormDnsProcessManager(private val context: Context) {

    companion object {
        private const val TAG = "StormDnsProcessManager"
        const val SOCKS_PORT = 10853
        private const val STORM_LIB_NAME = "libstormdns_client.so"
        private const val COTTEN_LIB_NAME = "libcottendns_client.so"
    }

    private val processLock = Any()
    private var process: Process? = null
    private var activeConfigFile: File? = null
    private var activeResolversFile: File? = null
    private var drainThread: Thread? = null

    @Volatile
    var lastError: String? = null
        private set

    /**
     * Checks whether the native StormDNS / CottenDNS binary is installed and executable.
     */
    fun getExecutable(engine: String = "stormdns"): File {
        val isCotten = engine.equals("cottendns", ignoreCase = true)
        val preferred = if (isCotten) COTTEN_LIB_NAME else STORM_LIB_NAME
        val fallback = if (isCotten) STORM_LIB_NAME else COTTEN_LIB_NAME

        val primaryFile = File(context.applicationInfo.nativeLibraryDir, preferred)
        if (primaryFile.exists() && primaryFile.canExecute()) {
            return primaryFile
        }

        val fallbackFile = File(context.applicationInfo.nativeLibraryDir, fallback)
        if (fallbackFile.exists() && fallbackFile.canExecute()) {
            Log.w(TAG, "Preferred binary $preferred missing/not executable, falling back to $fallback")
            return fallbackFile
        }

        // Diagnose instead of guessing: distinguish "wrong build installed"
        // (nativeLibraryDir empty/without our libs) from "extraction disabled"
        // (extractNativeLibs=false leaves nothing on disk) from "ROM shipped the
        // files non-executable" (exists=true, canExecute=false).
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val present = libDir.list()?.sorted()?.joinToString() ?: "<dir missing>"
        throw IllegalStateException(
            "DNS tunnel binary not installed: $preferred in $libDir " +
                "[exists=${primaryFile.exists()}, canExecute=${primaryFile.canExecute()}] — " +
                "install the full APK; native libs present in libDir: $present"
        )
    }

    /**
     * Starts StormDNS / CottenDNS process pointing to the specified target domain and encryption parameters,
     * using the provided list of resolver IPs. [tune] applies an auto-tune MTU
     * profile; null keeps the built-in defaults.
     */
    @Synchronized
    fun start(
        domain: String,
        encryptionKey: String,
        encryptionMethod: Int,
        resolverIps: List<String>,
        socksPort: Int = SOCKS_PORT,
        engine: String = "stormdns",
        tune: DnsTunePreset? = null,
    ) {
        stop()

        val executable = getExecutable(engine)
        val runtimeDir = File(context.filesDir, "stormdns_runtime").apply { mkdirs() }
        cleanupStaleFiles(runtimeDir)

        val launchId = UUID.randomUUID().toString().take(8)
        val configFile = File(runtimeDir, ".storm-$launchId.toml")
        val resolversFile = File(runtimeDir, ".storm-$launchId.resolvers")

        val effectiveResolvers = if (resolverIps.isNotEmpty()) {
            resolverIps
        } else {
            listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9")
        }

        resolversFile.writeText(effectiveResolvers.joinToString("\n"))
        configFile.writeText(renderStormToml(domain, encryptionKey, encryptionMethod, socksPort, engine, tune))

        lastError = null
        activeConfigFile = configFile
        activeResolversFile = resolversFile

        Log.i(TAG, "Starting $engine process for domain $domain on SOCKS port $socksPort using ${executable.name}...")
        try {
            val startedProcess = ProcessBuilder(
                executable.absolutePath,
                "-config", configFile.absolutePath,
                "-resolvers", resolversFile.absolutePath,
            )
                .directory(runtimeDir)
                .redirectErrorStream(true)
                .start()

            val reader = startedProcess.inputStream.bufferedReader()
            val dt = thread(name = "StormDns-Drain", isDaemon = true) {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        Log.d(TAG, "[$engine] $line")
                        val lower = line.lowercase()
                        if ("no valid connections" in lower || "mtu tests failed" in lower) {
                            lastError = "No DNS resolver passed MTU testing"
                        } else if ("failed to parse" in lower || "syntax error" in lower) {
                            lastError = "Invalid config TOML: $line"
                        } else if ("fatal" in lower || "panic" in lower) {
                            lastError = line.trim()
                        }
                    }
                } catch (_: IOException) {
                    // process terminated
                }
            }

            synchronized(processLock) {
                process = startedProcess
                drainThread = dt
            }
        } catch (e: Exception) {
            cleanupFiles()
            throw IOException("Failed to spawn $engine process: ${e.message}", e)
        }
    }

    /**
     * Polls until the local SOCKS proxy port is accepting connections or timeout expires.
     */
    fun waitForPort(socksPort: Int = SOCKS_PORT, timeoutMillis: Long = 35_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            synchronized(processLock) {
                val p = process
                if (p == null || !p.isAlive) {
                    val exitCode = p?.exitValue()
                    Log.e(TAG, "DNS tunnel process exited before SOCKS port was ready (exitCode: $exitCode, lastError: $lastError)")
                    return false
                }
            }

            val connected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", socksPort), 250)
                }
                true
            }.getOrDefault(false)

            if (connected) {
                Log.i(TAG, "DNS tunnel SOCKS5 proxy is ready on 127.0.0.1:$socksPort")
                return true
            }

            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        Log.e(TAG, "Timed out waiting for DNS tunnel SOCKS port $socksPort")
        return false
    }

    /**
     * Stops the running StormDNS process and deletes temporary config files.
     */
    @Synchronized
    fun stop(gracePeriodMillis: Long = 1_000) {
        val activeProcess: Process?
        synchronized(processLock) {
            activeProcess = process
            process = null
            drainThread = null
        }

        if (activeProcess != null) {
            Log.i(TAG, "Stopping StormDNS process...")
            activeProcess.destroy()
            try {
                activeProcess.waitFor(gracePeriodMillis, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (activeProcess.isAlive) {
                activeProcess.destroyForcibly()
            }
        }

        cleanupFiles()
    }

    private fun cleanupFiles() {
        runCatching { activeConfigFile?.delete() }
        runCatching { activeResolversFile?.delete() }
        activeConfigFile = null
        activeResolversFile = null
    }

    private fun cleanupStaleFiles(dir: File) {
        dir.listFiles()?.filter { it.name.startsWith(".storm-") }?.forEach {
            runCatching { it.delete() }
        }
    }
}
