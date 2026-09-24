package com.ahoura.asha_scanner_ip.core.storm

import com.ahoura.asha_scanner_ip.core.net.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * StormDNS/CottenDNS auto-tune, ported from WhiteDNS-Android's measured-approach:
 * DNS-tunnel throughput on Iranian carriers is dominated by the MTU / duplication
 * / compression profile, and no single profile fits every carrier. These presets
 * are field-measured starting points; [runTune] tries them one by one against the
 * user's actual resolvers, benchmarks each live tunnel, and keeps the fastest.
 */
data class DnsTunePreset(
    val id: String,
    val label: String,
    val minUploadMtu: Int,
    val maxUploadMtu: Int,
    val minDownloadMtu: Int,
    val maxDownloadMtu: Int,
    val resolverTimeoutSeconds: Double,
    val fragmentStoreCapacity: Int,
    val uploadDuplication: Int,
    val downloadDuplication: Int,
    val uploadCompression: Int,
    val downloadCompression: Int,
    /** Aggressive profiles win on some carriers and stall on others. */
    val aggressive: Boolean = false,
)

object StormAutoTunePresets {

    val all: List<DnsTunePreset> = listOf(
        DnsTunePreset("iran-average", "Iran Default", 40, 140, 300, 3000, 2.5, 256, 3, 7, 2, 2),
        DnsTunePreset("iran-low-mtu-scan", "Iran Low MTU Scan", 20, 120, 160, 768, 2.5, 256, 3, 7, 2, 2),
        DnsTunePreset("iran-fast-low-mtu", "Iran Fast Low MTU", 20, 325, 100, 1270, 2.5, 100, 5, 10, 2, 2),
        DnsTunePreset("iran-compact-fixed", "Iran Compact Fixed", 62, 62, 414, 414, 2.5, 384, 6, 8, 2, 2),
        DnsTunePreset("iran-fixed-64-balanced", "Iran Fixed 64 Balanced", 64, 64, 756, 756, 2.5, 256, 8, 8, 2, 2),
        DnsTunePreset("iran-mid-reliable", "Iran Mid Reliable", 120, 160, 652, 1110, 2.5, 256, 5, 11, 2, 2),
        DnsTunePreset("iran-download-heavy", "Iran Download Heavy", 104, 139, 394, 1000, 2.5, 256, 8, 30, 2, 2),
        DnsTunePreset("iran-fixed-64-aggressive", "Iran Fixed 64 Wide", 64, 64, 756, 1317, 2.5, 230, 14, 30, 2, 2, aggressive = true),
        DnsTunePreset("iran-large-download-aggressive", "Iran No Compression Max", 100, 600, 800, 6500, 2.5, 640, 23, 30, 0, 0, aggressive = true),
        DnsTunePreset("iran-wide-range-aggressive", "Iran Wide Range Max", 100, 1000, 200, 2667, 2.5, 256, 15, 30, 2, 2, aggressive = true),
    )

    val stable: List<DnsTunePreset> get() = all.filter { !it.aggressive }

    fun byId(id: String): DnsTunePreset? = all.firstOrNull { it.id == id }
}

/**
 * Renders the client TOML. Pure so the unit suite can lock the exact profile
 * values that reach the native binary.
 */
internal fun renderStormToml(
    domain: String,
    encryptionKey: String,
    encryptionMethod: Int,
    socksPort: Int,
    engine: String,
    tune: DnsTunePreset?,
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
    appendLine("UPLOAD_PACKET_DUPLICATION_COUNT = ${tune?.uploadDuplication ?: 0}")
    appendLine("DOWNLOAD_PACKET_DUPLICATION_COUNT = ${tune?.downloadDuplication ?: 0}")
    appendLine("UPLOAD_COMPRESSION_TYPE = ${tune?.uploadCompression ?: 1}")
    appendLine("DOWNLOAD_COMPRESSION_TYPE = ${tune?.downloadCompression ?: 1}")
    appendLine("BASE_ENCODE_DATA = true")
    appendLine("MIN_UPLOAD_MTU = ${tune?.minUploadMtu ?: 40}")
    appendLine("MIN_DOWNLOAD_MTU = ${tune?.minDownloadMtu ?: 300}")
    appendLine("MAX_UPLOAD_MTU = ${tune?.maxUploadMtu ?: 140}")
    appendLine("MAX_DOWNLOAD_MTU = ${tune?.maxDownloadMtu ?: 3000}")
    appendLine("MTU_TEST_RETRIES_RESOLVERS = 3")
    appendLine("MTU_TEST_TIMEOUT_RESOLVERS = ${tune?.resolverTimeoutSeconds ?: 2.5}")
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
    appendLine("DNS_RESPONSE_FRAGMENT_STORE_CAPACITY = ${tune?.fragmentStoreCapacity ?: 256}")
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

object StormAutoTune {

    /** Fixed benchmark target: Cloudflare's own speed endpoint (public, constant). */
    private const val BENCH_HOST = "speed.cloudflare.com"
    private const val BENCH_PORT = 443
    private const val BENCH_BYTES = 300_000

    /**
     * Policy guard for outbound benchmark targets: refuse loopback, link-local,
     * site-local (private), multicast and carrier-grade NAT ranges so the
     * benchmark can never be pointed at infrastructure addresses.
     */
    internal fun validatePublicHost(host: String) {
        val cleaned = host.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
        require(cleaned.isNotBlank()) { "empty benchmark host" }
        val addresses: List<InetAddress> = runCatching {
            listOf(InetAddress.getByName(cleaned))
        }.getOrElse {
            InetAddress.getAllByName(cleaned).toList()
        }
        require(addresses.isNotEmpty()) { "unresolvable benchmark host" }
        for (a in addresses) {
            val bad = a.isLoopbackAddress || a.isAnyLocalAddress || a.isLinkLocalAddress ||
                a.isSiteLocalAddress || a.isMulticastAddress ||
                runCatching { a.address.size == 4 && (a.address[0].toInt() and 0xFF) == 100 }
                    .getOrDefault(false)   // 100.64/10 CGNAT
            require(!bad) { "benchmark host must be public: $cleaned" }
        }
    }

    /**
     * Downloads [BENCH_BYTES] through the local SOCKS listener and returns the
     * achieved rate in KB/s, or null if too little data moved to be meaningful.
     */
    suspend fun socksBenchmarkKbps(port: Int, budgetMs: Long = 9_000): Long? =
        withContext(Dispatchers.IO) {
            validatePublicHost(BENCH_HOST)
            var socket: Socket? = null
            var ssl: SSLSocket? = null
            try {
                // A SOCKS-proxied Socket: the JDK performs the CONNECT against
                // 127.0.0.1:port, handing the tunnel the benchmark hostname for
                // remote resolution — exactly what the tunnel will carry live.
                val plain = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
                plain.tcpNoDelay = true
                plain.connect(InetSocketAddress(BENCH_HOST, BENCH_PORT), 8_000)
                ssl = Tls.handshake(
                    plain, BENCH_HOST, BENCH_PORT, BENCH_HOST,
                    alpn = listOf("http/1.1"), insecure = false, handshakeTimeoutMs = 8_000,
                )
                ssl.soTimeout = 4_000

                val req = buildString {
                    append("GET /__down?bytes=").append(BENCH_BYTES).append(" HTTP/1.1\r\n")
                    append("Host: ").append(BENCH_HOST).append("\r\n")
                    append("User-Agent: Mozilla/5.0\r\n")
                    append("Accept: */*\r\n")
                    append("Connection: close\r\n\r\n")
                }
                val startNs = System.nanoTime()
                val deadline = startNs + budgetMs * 1_000_000
                ssl.outputStream.write(req.toByteArray(Charsets.US_ASCII))
                ssl.outputStream.flush()

                val input = ssl.inputStream
                val buf = ByteArray(16 * 1024)
                var headerDone = false
                var headerTail = 0
                var bodyBytes = 0L
                while (System.nanoTime() < deadline) {
                    val n = try {
                        input.read(buf)
                    } catch (_: IOException) {
                        break   // read timeout with the budget spent is fine
                    }
                    if (n < 0) break
                    if (!headerDone) {
                        var i = 0
                        while (i < n) {
                            val b = buf[i].toInt()
                            headerTail = when {
                                headerTail == 0 && b == '\r'.code -> 1
                                headerTail == 1 && b == '\n'.code -> 2
                                headerTail == 2 && b == '\r'.code -> 3
                                headerTail == 3 && b == '\n'.code -> 4
                                else -> if (b == '\r'.code) 1 else 0
                            }
                            i++
                            if (headerTail == 4) {
                                headerDone = true
                                break
                            }
                        }
                        if (headerDone) bodyBytes += (n - i)
                    } else {
                        bodyBytes += n
                    }
                }
                val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1)
                // Rate counts wall time including TLS; benchmarks the user
                // actually experiences, not the best-case transfer window.
                if (bodyBytes < 8 * 1024) return@withContext null
                (bodyBytes / 1024) / (elapsedNs / 1_000_000_000.0).toLong().coerceAtLeast(1)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            } finally {
                runCatching { ssl?.close() }
                runCatching { socket?.close() }
            }
        }

    /**
     * Benchmarks each preset against the live resolver set. One tunnel at a
     * time — parallel tunnels would fight over the same UDP:53 budget and
     * corrupt every measurement.
     *
     * @return the fastest preset, or null when nothing benchmarked.
     */
    suspend fun runTune(
        manager: StormDnsProcessManager,
        domain: String,
        encryptionKey: String,
        encryptionMethod: Int,
        resolverIps: List<String>,
        engine: String,
        presets: List<DnsTunePreset>,
        isCancelled: () -> Boolean = { false },
        onResult: (preset: DnsTunePreset, kbps: Long?, error: String?) -> Unit,
    ): DnsTunePreset? {
        var best: DnsTunePreset? = null
        var bestKbps = -1L
        for (preset in presets) {
            if (isCancelled()) break
            try {
                manager.start(
                    domain = domain,
                    encryptionKey = encryptionKey,
                    encryptionMethod = encryptionMethod,
                    resolverIps = resolverIps,
                    engine = engine,
                    tune = preset,
                )
                if (!manager.waitForPort()) {
                    onResult(preset, null, manager.lastError?.ifBlank { null } ?: "tunnel did not come up")
                    continue
                }
                val kbps = socksBenchmarkKbps(StormDnsProcessManager.SOCKS_PORT)
                onResult(preset, kbps, if (kbps == null) "no benchmark data" else null)
                if (kbps != null && kbps > bestKbps) {
                    bestKbps = kbps
                    best = preset
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onResult(preset, null, e.message ?: "start failed")
            } finally {
                runCatching { manager.stop() }
            }
        }
        return best
    }
}
