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
     * using the provided list of resolver IPs.
     */
    @Synchronized
    fun start(
        domain: String,
        encryptionKey: String,
        encryptionMethod: Int,
        resolverIps: List<String>,
        socksPort: Int = SOCKS_PORT,
        engine: String = "stormdns",
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
        configFile.writeText(renderTomlConfig(domain, encryptionKey, encryptionMethod, socksPort, engine))

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

    private fun renderTomlConfig(
        domain: String,
        encryptionKey: String,
        encryptionMethod: Int,
        socksPort: Int,
        engine: String = "stormdns",
    ): String = buildString {
        appendLine("DOMAINS = [\"${domain.trim().trimEnd('.')}\"]")
        appendLine("DATA_ENCRYPTION_METHOD = $encryptionMethod")
        appendLine("ENCRYPTION_KEY = \"${encryptionKey.trim()}\"")
        appendLine("PROTOCOL_TYPE = \"udp\"")
        appendLine("LISTEN_IP = \"127.0.0.1\"")
        appendLine("LISTEN_PORT = $socksPort")
        appendLine("SOCKS5_AUTH = false")
        appendLine("SOCKS5_USER = \"\"")
        appendLine("SOCKS5_PASS = \"\"")
        appendLine("LOCAL_DNS_ENABLED = false")
        appendLine("LOCAL_DNS_IP = \"127.0.0.1\"")
        appendLine("LOCAL_DNS_PORT = 0")
        appendLine("RESOLVER_BALANCING_STRATEGY = 1")
        appendLine("UPLOAD_PACKET_DUPLICATION_COUNT = 0")
        appendLine("DOWNLOAD_PACKET_DUPLICATION_COUNT = 0")
        appendLine("UPLOAD_COMPRESSION_TYPE = 1")
        appendLine("DOWNLOAD_COMPRESSION_TYPE = 1")
        appendLine("BASE_ENCODE_DATA = true")
        appendLine("MIN_UPLOAD_MTU = 40")
        appendLine("MIN_DOWNLOAD_MTU = 300")
        appendLine("MAX_UPLOAD_MTU = 140")
        appendLine("MAX_DOWNLOAD_MTU = 3000")
        appendLine("MTU_TEST_RETRIES_RESOLVERS = 3")
        appendLine("MTU_TEST_TIMEOUT_RESOLVERS = 2.5")
        appendLine("MTU_TEST_PARALLELISM_RESOLVERS = 100")
        appendLine("MTU_TEST_RETRIES_LOGS = 5")
        appendLine("MTU_TEST_TIMEOUT_LOGS = 2.5")
        appendLine("MTU_TEST_PARALLELISM_LOGS = 32")
        appendLine("RX_TX_WORKERS = 4")
        appendLine("TUNNEL_PROCESS_WORKERS = 4")
        appendLine("TUNNEL_PACKET_TIMEOUT_SECONDS = 10.0")
        appendLine("DISPATCHER_IDLE_POLL_INTERVAL_SECONDS = 0.020")
        appendLine("TX_CHANNEL_SIZE = 2048")
        appendLine("RX_CHANNEL_SIZE = 2048")
        appendLine("RESOLVER_UDP_CONNECTION_POOL_SIZE = 64")
        appendLine("STREAM_QUEUE_INITIAL_CAPACITY = 1024")
        appendLine("ORPHAN_QUEUE_INITIAL_CAPACITY = 256")
        appendLine("DNS_RESPONSE_FRAGMENT_STORE_CAPACITY = 256")
        appendLine("MAX_ACTIVE_STREAMS = 256")
        appendLine("LOCAL_HANDSHAKE_TIMEOUT_SECONDS = 15")
        appendLine("SOCKS_UDP_ASSOCIATE_READ_TIMEOUT_SECONDS = 30")
        appendLine("CLIENT_TERMINAL_STREAM_RETENTION_SECONDS = 60")
        appendLine("CLIENT_CANCELLED_SETUP_RETENTION_SECONDS = 15")
        appendLine("SESSION_INIT_RETRY_BASE_SECONDS = 1")
        appendLine("SESSION_INIT_RETRY_STEP_SECONDS = 1")
        appendLine("SESSION_INIT_RETRY_LINEAR_AFTER = 5")
        appendLine("SESSION_INIT_RETRY_MAX_SECONDS = 10")
        appendLine("SESSION_INIT_BUSY_RETRY_INTERVAL_SECONDS = 1")
        appendLine("STARTUP_MODE = \"resolvers\"")
        appendLine("LOG_SCAN_MAX_DAYS = 14")
        appendLine("LOG_SCAN_MAX_RESOLVERS = 128")
        appendLine("LOG_BASED_MTU_VERIFY = true")
        appendLine("STATS_REPORT_INTERVAL_SECONDS = 1.0")
        appendLine("PING_WATCHDOG_TIMEOUT_SECONDS = 30")
        appendLine("LOG_LEVEL = \"info\"")
        appendLine("LOG_TO_FILE = false")
        appendLine("LOG_DIR = \"logs\"")

        if (engine.equals("cottendns", ignoreCase = true)) {
            appendLine("CONFIG_PRESET = \"performance\"")
            appendLine("LEGACY_SESSION_ID = false")
            appendLine("RESOLVER_TRANSPORT = \"udp\"")
            appendLine("QUERY_TYPES = [\"TXT\"]")
            appendLine("QNAME_LABEL_LENGTH = 63")
            appendLine("FAST_CONNECT = true")
            appendLine("MTU_BACKGROUND_PARALLELISM = 10")
            appendLine("RESOLVER_RATE_LIMIT_ENABLED = true")
        }
    }
}
