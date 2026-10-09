package com.ahoura.asha_scanner_ip.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

enum class MtuVerdict {
    Fits,
    TooBig,
    NoReply,
    Unavailable,
}

data class MtuOverhead(val total: Int, val parts: List<Part>) {
    data class Part(val name: String, val bytes: Int)

    fun explain(): String =
        parts.joinToString(" + ") { "${it.name} ${it.bytes}" } + " = $total bytes"
}

object MtuOverheads {
    private const val IPV4 = 20
    private const val IPV6 = 40
    private const val UDP = 8
    private const val TCP = 32
    private const val TLS = 21
    private const val WEBSOCKET = 8
    private const val GRPC = 14
    private const val XHTTP = 9
    private const val QUIC = 29
    private const val WIREGUARD = 32
    private const val SHADOWSOCKS = 34

    fun of(
        protocol: String = "wireguard",
        network: String = "udp",
        tls: Boolean = true,
        ipv6: Boolean = false,
    ): MtuOverhead {
        val parts = mutableListOf<MtuOverhead.Part>()
        parts += MtuOverhead.Part(if (ipv6) "IPv6" else "IPv4", if (ipv6) IPV6 else IPV4)

        val proto = protocol.lowercase()
        when {
            proto.contains("wireguard") || proto.contains("amnezia") || proto.contains("gool") -> {
                parts += MtuOverhead.Part("UDP", UDP)
                parts += MtuOverhead.Part("WireGuard", WIREGUARD)
            }
            proto.contains("masque") || proto.contains("h3") || proto.contains("hysteria") -> {
                parts += MtuOverhead.Part("UDP", UDP)
                parts += MtuOverhead.Part("QUIC", QUIC)
                if (tls) parts += MtuOverhead.Part("TLS", TLS)
            }
            else -> {
                val net = network.lowercase()
                if (net == "quic") {
                    parts += MtuOverhead.Part("UDP", UDP)
                    parts += MtuOverhead.Part("QUIC", QUIC)
                } else {
                    parts += MtuOverhead.Part("TCP", TCP)
                    if (tls) parts += MtuOverhead.Part("TLS", TLS)
                    when (net) {
                        "ws", "websocket", "httpupgrade" -> parts += MtuOverhead.Part("WebSocket", WEBSOCKET)
                        "grpc", "gun" -> parts += MtuOverhead.Part("gRPC", GRPC)
                        "xhttp", "splithttp" -> parts += MtuOverhead.Part("XHTTP", XHTTP)
                    }
                }
                if (proto.contains("shadowsocks")) {
                    parts += MtuOverhead.Part("Shadowsocks", SHADOWSOCKS)
                }
            }
        }
        return MtuOverhead(parts.sumOf { it.bytes }, parts)
    }

    fun standard(ipv6: Boolean = false): MtuOverhead =
        of(protocol = "wireguard", network = "udp", tls = false, ipv6 = ipv6)
}

sealed interface MtuResult {
    data class Measured(
        val pathMtu: Int,
        val recommended: Int,
        val linkMtu: Int?,
        val host: String,
        val overhead: MtuOverhead,
        val confirmed: Boolean,
        val toServer: Boolean,
    ) : MtuResult

    data object VpnActive : MtuResult
    data class NotMeasurable(val linkMtu: Int?) : MtuResult
}

object AndroidMtuProbe {
    private val RECEIVED = Regex("(\\d+)\\s+received")

    fun linkMtu(context: Context): Int? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching null
        val network = cm.activeNetwork ?: return@runCatching null
        cm.getLinkProperties(network)?.mtu?.takeIf { it in 576..9000 }
    }.getOrNull()

    fun vpnActive(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrDefault(false)

    fun ping(host: String, payload: Int, timeoutMs: Int): MtuVerdict {
        val seconds = (timeoutMs / 1000).coerceAtLeast(1)
        val process = runCatching {
            ProcessBuilder(
                "/system/bin/ping",
                "-M", "do",
                "-s", payload.toString(),
                "-c", "1",
                "-W", seconds.toString(),
                host,
            ).redirectErrorStream(true).start()
        }.getOrNull() ?: return MtuVerdict.Unavailable

        val output = runCatching {
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(seconds + 2L, TimeUnit.SECONDS)) process.destroy()
            text
        }.getOrElse {
            runCatching { process.destroy() }
            return MtuVerdict.Unavailable
        }

        return classify(output)
    }

    private fun classify(output: String): MtuVerdict {
        val lower = output.lowercase()
        return when {
            "message too long" in lower -> MtuVerdict.TooBig
            "frag needed" in lower -> MtuVerdict.TooBig
            RECEIVED.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { it > 0 } == true ->
                MtuVerdict.Fits
            else -> MtuVerdict.NoReply
        }
    }
}

object MtuOptimizer {
    private const val IP_ICMP_OVERHEAD = 28
    const val MIN_MTU = 1280
    const val MAX_MTU = 1500

    private val FALLBACK_TARGETS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "217.218.155.155")
    private const val TIMEOUT_MS = 2_000
    private const val RETRIES = 2

    suspend fun optimize(
        context: Context,
        serverHost: String? = null,
        overhead: MtuOverhead = MtuOverheads.standard(),
    ): MtuResult = withContext(Dispatchers.IO) {
        val link = AndroidMtuProbe.linkMtu(context)
        if (AndroidMtuProbe.vpnActive(context)) return@withContext MtuResult.VpnActive

        val candidates = listOfNotNull(serverHost?.takeIf { it.isNotBlank() }) + FALLBACK_TARGETS
        val host = candidates.firstOrNull { fits(it, 64) }
            ?: return@withContext MtuResult.NotMeasurable(link)
        val toServer = serverHost != null && host == serverHost

        val ceiling = (link ?: MAX_MTU).coerceAtMost(MAX_MTU)
        var lo = 64
        var hi = ceiling - IP_ICMP_OVERHEAD
        if (hi <= lo) {
            val path = lo + IP_ICMP_OVERHEAD
            return@withContext measured(path, link, host, overhead, confirmed = false, toServer)
        }
        while (lo < hi) {
            val mid = lo + (hi - lo + 1) / 2
            if (fits(host, mid)) lo = mid else hi = mid - 1
        }

        val confirmed = fits(host, lo) && (lo >= ceiling - IP_ICMP_OVERHEAD || !fits(host, lo + 1))
        val path = lo + IP_ICMP_OVERHEAD
        measured(path, link, host, overhead, confirmed, toServer)
    }

    private fun measured(
        path: Int,
        link: Int?,
        host: String,
        overhead: MtuOverhead,
        confirmed: Boolean,
        toServer: Boolean,
    ) = MtuResult.Measured(
        pathMtu = path,
        recommended = recommend(path, overhead.total),
        linkMtu = link,
        host = host,
        overhead = overhead,
        confirmed = confirmed,
        toServer = toServer
    )

    private fun fits(host: String, payload: Int): Boolean {
        repeat(RETRIES + 1) {
            when (runCatching { AndroidMtuProbe.ping(host, payload, TIMEOUT_MS) }.getOrDefault(MtuVerdict.Unavailable)) {
                MtuVerdict.Fits -> return true
                MtuVerdict.TooBig -> return false
                MtuVerdict.NoReply -> Unit
                MtuVerdict.Unavailable -> return false
            }
        }
        return false
    }

    fun recommend(pathMtu: Int, overheadBytes: Int): Int {
        var optimal = pathMtu - overheadBytes - 32
        optimal -= (optimal % 16)
        return optimal.coerceIn(MIN_MTU, 1460)
    }
}
