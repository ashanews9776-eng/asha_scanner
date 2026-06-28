package com.ahoura.asha_scanner_ip.core.model

/**
 * Domain models for the scanner. These are a faithful Kotlin port of the data
 * structures used by SenPaiScanner (Go) — config, parsed proxy, probe modes and
 * results — adapted to Android/Kotlin idioms.
 */

/** Phase-1 probe handshake depth. Escalating levels of verification. */
enum class ProbeMode(val wire: String) {
    TCP("tcp"),   // raw TCP connect only
    TLS("tls"),   // TCP + TLS handshake (SNI)
    HTTP("http"); // TCP + TLS + HTTPS GET /cdn-cgi/trace

    companion object {
        fun from(s: String?): ProbeMode =
            entries.firstOrNull { it.wire.equals(s?.trim(), ignoreCase = true) } ?: HTTP
    }
}

/** Supported proxy protocols for the config the user pastes. */
enum class Protocol(val scheme: String) {
    VLESS("vless"),
    TROJAN("trojan");

    companion object {
        fun fromScheme(s: String): Protocol? =
            entries.firstOrNull { it.scheme.equals(s, ignoreCase = true) }
    }
}

/**
 * A parsed VLESS / Trojan link. Mirrors the fields extracted by the original
 * xraytest parser so a Phase-2 validator (pure-Kotlin or xray-core) has
 * everything it needs to dial the endpoint.
 */
data class ProxyConfig(
    val protocol: Protocol,
    val address: String,          // original host (domain or IP) from the link
    val port: Int,
    val uuid: String = "",        // VLESS id
    val password: String = "",    // Trojan password
    val encryption: String = "none",
    val flow: String = "",
    val security: String = "none", // none | tls | reality
    val sni: String = "",
    val fingerprint: String = "",
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,
    val network: String = "tcp",   // tcp | ws | grpc | xhttp | splithttp
    val path: String = "/",
    val hostHeader: String = "",   // ws/xhttp Host header
    val serviceName: String = "",  // grpc
    val mode: String = "",         // grpc/xhttp mode
    val publicKey: String = "",    // reality pbk
    val shortId: String = "",      // reality sid
    val remark: String = "",
    val raw: String = "",
) {
    /** Effective SNI to present when probing/validating an edge for this config. */
    fun effectiveSni(): String = sni.ifBlank { hostHeader.ifBlank { address } }
}

/** User-tunable scan parameters. Defaults mirror SenPaiScanner's ScanDefaults. */
data class ScanConfig(
    val count: Int = 500,
    val concurrency: Int = 50,
    val timeoutMs: Long = 5_000,
    val tries: Int = 4,
    val ports: List<Int> = listOf(443),
    val mode: ProbeMode = ProbeMode.HTTP,
    val useV4: Boolean = true,
    val useV6: Boolean = false,
    val top: Int = 10,
    val speedTest: Boolean = true,   // run Phase-2 throughput validation on the top results
    val smartStop: Boolean = true,   // stop Phase-1 early once plenty of healthy IPs are found
    val sniOverride: String = "",    // optional manual SNI; blank -> rotate/derive
    val extraCidrs: List<String> = emptyList(),
    // Precise CF IPv4 ranges loaded from the bundled asset (ircfspace list).
    // Empty -> fall back to the small official CloudflareRanges.V4 constant.
    val customV4Ranges: List<String> = emptyList(),
    // When non-empty, scan exactly these IPs instead of generating random ones
    // (Test-IPs mode).
    val explicitIps: List<String> = emptyList(),
    // Open-site fallback: if Phase-1 finds zero healthy IPs, resolve these
    // Cloudflare-fronted domains and probe their edge IPs instead. Loaded from
    // the bundled cf_domains.txt asset, optionally extended by the user.
    val fallbackToDomains: Boolean = true,
    val fallbackDomains: List<String> = emptyList(),
) {
    companion object {
        /** Common Cloudflare-friendly TLS ports, as offered by the original UI. */
        val COMMON_PORTS = listOf(443, 8443, 2053, 2083, 2087, 2096)
        val COUNT_PRESETS = listOf(100, 500, 1000, 5000, 10000, 20000)
    }
}

/**
 * Per-IP scan outcome. Aggregates the [tries] latency samples plus optional
 * Phase-2 throughput. [healthy] is computed by the engine.
 */
data class ScanResult(
    val ip: String,
    val port: Int,
    val mode: ProbeMode,
    val latenciesMs: List<Long> = emptyList(),
    val attempts: Int = 0,
    val tlsOk: Boolean = false,
    val httpStatus: Int = 0,
    val colo: String = "",
    val throughputBytesPerSec: Double = 0.0,
    val speedTested: Boolean = false,
    val healthy: Boolean = false,
    val timestamp: Long = 0L,
) {
    val endpoint: String get() = if (ip.contains(":")) "[$ip]:$port" else "$ip:$port"

    /** Average latency of successful samples, in ms. 0 if none. */
    val avgLatencyMs: Double
        get() = if (latenciesMs.isEmpty()) 0.0 else latenciesMs.average()

    /** Min latency, ms. */
    val minLatencyMs: Long get() = latenciesMs.minOrNull() ?: 0L

    /** Jitter = max-min of latency samples, ms. */
    val jitterMs: Long
        get() = if (latenciesMs.size < 2) 0L else (latenciesMs.max() - latenciesMs.min())

    /** Packet loss fraction in [0,1] given the number of attempts. */
    val loss: Double
        get() = if (attempts <= 0) 1.0 else 1.0 - (latenciesMs.size.toDouble() / attempts)

    /** Throughput in Mbps for display. */
    val throughputMbps: Double get() = throughputBytesPerSec * 8.0 / 1_000_000.0
}

/** Lifecycle of a scan run. */
enum class ScanPhase { IDLE, PROBING, RESOLVING, VALIDATING, DONE, CANCELLED, ERROR }

/** Streamed progress snapshot emitted by the engine while scanning. */
data class ScanProgress(
    val phase: ScanPhase = ScanPhase.IDLE,
    val tested: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val validated: Int = 0,
    val validateTotal: Int = 0,
    val elapsedMs: Long = 0L,
    val best: List<ScanResult> = emptyList(),
    val error: String? = null,
    // True once the engine has switched to the open-site fallback (probing IPs
    // resolved from Cloudflare-fronted domains because the range scan found none).
    val usingFallback: Boolean = false,
    // Rolling window of the most recent probe latencies (ms) for the live
    // oscilloscope. A value of 0 marks a miss/timeout (drawn as a dropout spike).
    // Bounded by the engine, oldest-first, so the UI just plots it left→right.
    val latencyTrace: List<Int> = emptyList(),
)
