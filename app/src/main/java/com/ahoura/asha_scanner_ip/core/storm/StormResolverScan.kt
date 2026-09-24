package com.ahoura.asha_scanner_ip.core.storm

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The WhiteDNS-ported resolver scan engine.
 *
 * Unlike a plain UDP probe, this asks the REAL question: "can this resolver
 * carry OUR DNS tunnel?" — the native StormDNS/CottenDNS binary is spawned in
 * scan mode (STARTUP_MODE=resolvers, no SOCKS listener) with a chunk of
 * candidate resolvers, and it handshakes the actual tunnel through each one,
 * emitting `WD_SCAN` telemetry on stdout:
 *
 *   WD_SCAN event=valid resolver=<ip>
 *   WD_SCAN event=rejected resolver=<ip>
 *   WD_SCAN event=complete total=%d valid=%d rejected=%d
 *
 * The pool is split into chunks with [chunkResolversRoundRobin] and each chunk
 * runs in its own process, exactly like the upstream app — parallelism comes
 * from process count, not threads inside one process.
 */
object StormResolverScan {

    private const val TAG = "StormResolverScan"

    // ── Chunker (verbatim port of ScanResolverChunker) ────────────────────────

    /** Splits [resolvers] into [requestedWorkerCount] round-robin chunks. */
    fun chunkResolversRoundRobin(
        resolvers: List<String>,
        requestedWorkerCount: Int,
    ): List<List<String>> {
        val cleanResolvers = resolvers
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (cleanResolvers.isEmpty()) {
            return emptyList()
        }

        val workerCount = requestedWorkerCount
            .coerceAtLeast(1)
            .coerceAtMost(cleanResolvers.size)
        val chunks = List(workerCount) { mutableListOf<String>() }
        cleanResolvers.forEachIndexed { index, resolver ->
            chunks[index % workerCount] += resolver
        }
        return chunks.map { it.toList() }
    }

    // ── Telemetry parser (verbatim port of StormDnsScanTelemetry) ─────────────

    sealed class ScanTelemetry {
        data class Valid(val resolver: String) : ScanTelemetry()
        data class Rejected(val resolver: String) : ScanTelemetry()
        data class Complete(val total: Int, val valid: Int, val rejected: Int) : ScanTelemetry()
        data class Progress(val phase: String, val percent: Int) : ScanTelemetry()
    }

    private val AnsiEscapeRegex = Regex("${27.toChar()}\\[[;?0-9]*[ -/]*[@-~]")
    private val ScanFieldRegex = Regex("""(\w+)=([^\s]+)""")
    private const val ScanMarker = "WD_SCAN"
    private const val ProgressMarker = "WD_PROGRESS"

    /** Parses one stdout line; null for anything that is not scan telemetry. */
    fun parseScanLine(line: String): ScanTelemetry? {
        val cleanLine = line
            .replace(AnsiEscapeRegex, "")
            .trim()
        val markerIndex = cleanLine.indexOf(ScanMarker)
        if (markerIndex >= 0) {
            val fields = ScanFieldRegex.findAll(cleanLine.substring(markerIndex + ScanMarker.length))
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
            return when (fields["event"]) {
                "valid" -> fields["resolver"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(ScanTelemetry::Valid)
                "rejected" -> fields["resolver"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(ScanTelemetry::Rejected)
                "complete" -> ScanTelemetry.Complete(
                    total = fields["total"].toIntOrZero(),
                    valid = fields["valid"].toIntOrZero(),
                    rejected = fields["rejected"].toIntOrZero(),
                )
                else -> null
            }
        }
        val progressIndex = cleanLine.indexOf(ProgressMarker)
        if (progressIndex >= 0) {
            val fields = ScanFieldRegex.findAll(cleanLine.substring(progressIndex + ProgressMarker.length))
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
            return ScanTelemetry.Progress(
                phase = fields["phase"].orEmpty(),
                percent = fields["percent"].toIntOrZero(),
            )
        }
        return null
    }

    private fun String?.toIntOrZero(): Int = this?.toIntOrNull() ?: 0

    // ── Scan-mode TOML (port of renderScanClientToml) ─────────────────────────

    /**
     * Scan mode config: resolvers startup, no SOCKS listener (port 0), single
     * internal MTU-test thread — the process IS the scan worker.
     */
    internal fun renderScanToml(
        domain: String,
        encryptionKey: String,
        encryptionMethod: Int,
        engine: String,
    ): String = buildString {
        appendLine("DOMAINS = [\"${domain.trim().trimEnd('.')}\"]")
        appendLine("DATA_ENCRYPTION_METHOD = $encryptionMethod")
        appendLine("ENCRYPTION_KEY = \"${encryptionKey.trim()}\"")
        appendLine("PROTOCOL_TYPE = \"udp\"")
        appendLine("LISTEN_IP = \"127.0.0.1\"")
        appendLine("LISTEN_PORT = 0")
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
        appendLine("MTU_TEST_PARALLELISM_RESOLVERS = 1")
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

    // ── Result store ─────────────────────────────────────────────────────────

    /**
     * Persists the valid-resolver set so a stopped scan survives process death
     * and resume skips what already passed — the upstream ScannerResultStore.
     */
    class ScannerResultStore(private val file: File) {

        @Synchronized
        fun load(): Set<String> = runCatching {
            if (!file.exists()) emptySet() else file.readLines().map(String::trim).filter(String::isNotEmpty).toSet()
        }.getOrDefault(emptySet())

        @Synchronized
        fun save(validIps: Set<String>) {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(validIps.sorted().joinToString("\n"))
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        }

        @Synchronized
        fun clear() {
            runCatching { file.delete() }
        }
    }

    // ── Runner ───────────────────────────────────────────────────────────────

    /** Cap on simultaneous scan processes: each is a full Go runtime. */
    const val MAX_WORKER_PROCESSES = 8
    private const val CHUNK_TIMEOUT_MS = 15 * 60_000L

    class ScanHandle internal constructor(
        private val cancelled: AtomicBoolean,
        private val processes: () -> List<Process>,
    ) {
        /** True while at least one chunk process is still scanning. */
        fun isAlive(): Boolean = !cancelled.get() && processes().any { it.isAlive }

        /** Stops every running scan process; safe to call from any thread. */
        fun cancel() {
            cancelled.set(true)
            processes().forEach { p ->
                runCatching { p.destroy() }
            }
        }
    }

    /**
     * Runs the scan over [resolvers] split into round-robin chunks, one native
     * process per chunk (up to [MAX_WORKER_PROCESSES] at once). Events stream
     * through the callbacks; the returned handle cancels everything.
     */
    fun start(
        context: Context,
        domain: String,
        encryptionKey: String,
        encryptionMethod: Int,
        engine: String,
        resolvers: List<String>,
        workerCount: Int,
        onEvent: (ScanTelemetry) -> Unit,
    ): ScanHandle? {
        val bin = runCatching {
            StormDnsProcessManager(context).getExecutable(engine)
        }.getOrNull() ?: return null

        val chunks = chunkResolversRoundRobin(resolvers, workerCount.coerceAtMost(MAX_WORKER_PROCESSES))
        if (chunks.isEmpty()) return null

        val cancelled = AtomicBoolean(false)
        val spawned = java.util.Collections.synchronizedList(mutableListOf<Process>())
        val runtimeDir = File(context.filesDir, "stormdns_runtime").apply { mkdirs() }

        val threads = chunks.mapIndexed { chunkIndex, chunk ->
            thread(name = "wdscan-$chunkIndex", isDaemon = true) {
                val launchId = UUID.randomUUID().toString().take(8)
                val configFile = File(runtimeDir, ".scan-$launchId.toml")
                val resolversFile = File(runtimeDir, ".scan-$launchId.resolvers")
                var process: Process? = null
                try {
                    configFile.writeText(renderScanToml(domain, encryptionKey, encryptionMethod, engine))
                    resolversFile.writeText(chunk.joinToString("\n"))

                    process = ProcessBuilder(bin.absolutePath, "-config", configFile.absolutePath, "-resolvers", resolversFile.absolutePath)
                        .directory(runtimeDir)
                        .redirectErrorStream(true)
                        .start()
                    spawned.add(process!!)

                    val reader = process!!.inputStream.bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        val telemetry = parseScanLine(line) ?: continue
                        onEvent(telemetry)
                        if (cancelled.get()) break
                    }
                } catch (e: Exception) {
                    if (!cancelled.get()) {
                        Log.w(TAG, "scan chunk $chunkIndex failed: ${e.message}")
                    }
                } finally {
                    runCatching { process?.destroy() }
                    spawned.remove(process)
                    runCatching { configFile.delete() }
                    runCatching { resolversFile.delete() }
                }
            }
        }

        // Watchdog: the engine exits by itself after the complete event; if a
        // chunk hangs past the timeout, tear it down so the scan can end.
        thread(name = "wdscan-watchdog", isDaemon = true) {
            val deadline = System.currentTimeMillis() + CHUNK_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (cancelled.get() || spawned.isEmpty()) break
                try {
                    Thread.sleep(1_000)
                } catch (_: InterruptedException) {
                    break
                }
            }
            if (!cancelled.get()) {
                spawned.toList().forEach { p ->
                    runCatching { p.destroy() }
                }
            }
        }

        return ScanHandle(cancelled) { spawned.toList() }
    }
}
