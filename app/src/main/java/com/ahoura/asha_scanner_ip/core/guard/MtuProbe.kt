package com.ahoura.asha_scanner_ip.core.guard

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Per-method outer-path MTU search.
 */
object MtuProbe {

    private const val TAG = "MtuProbe"

    private const val ICMP_OVERHEAD = 28
    private const val ICMP_TARGET = "1.1.1.1"
    private const val PROBE_TIMEOUT_MS = 900

    const val SCAN_FLOOR = 1280
    const val SCAN_CEIL = 1460

    private const val SAFETY_MARGIN = 32
    private const val SNAP = 16

    private val OVERHEAD = mapOf(
        MtuConfig.Method.MASQUE to 196,
        MtuConfig.Method.WIREGUARD to 60,
        MtuConfig.Method.WOW to 280,
        MtuConfig.Method.PSIPHON to 40,
        MtuConfig.Method.TOR to 100,
        MtuConfig.Method.SHARD to 70,
    )

    data class Result(
        val method: MtuConfig.Method,
        val outerPathMtu: Int?,
        val inner: Int?,
        val probes: Int,
        val capped: Boolean = false,
    )

    fun measure(
        context: Context,
        method: MtuConfig.Method,
        onProgress: (Int) -> Unit = {},
    ): Result {
        val localMtu = getInterfaceMtu(context)
        Log.i(TAG, "${method.title}: local interface MTU=$localMtu")

        var probes = 0
        fun testMtu(totalSize: Int): Boolean {
            val payload = totalSize - ICMP_OVERHEAD
            if (payload < 0) return true
            probes++
            onProgress(totalSize)
            return execPing(ICMP_TARGET, payload, PROBE_TIMEOUT_MS, dontFragment = true)
        }

        if (testMtu(2000)) {
            Log.w(TAG, "${method.title}: DF ignored on this path, using safe $SCAN_FLOOR")
            return Result(method, 2000, SCAN_FLOOR, probes)
        }

        var low = 1200
        var high = localMtu.coerceAtMost(1500)
        var bestPathMtu = 1200

        while (low <= high) {
            val mid = (low + high) / 2
            if (testMtu(mid)) {
                bestPathMtu = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
            try {
                Thread.sleep(90)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        val overhead = OVERHEAD[method] ?: 0
        var optimal = run {
            var o = bestPathMtu - overhead - SAFETY_MARGIN
            o -= o % SNAP
            o.coerceIn(SCAN_FLOOR, SCAN_CEIL)
        }

        Log.i(TAG, "${method.title}: pathMtu=$bestPathMtu overhead=$overhead optimal=$optimal after $probes probes localMtu=$localMtu")
        return Result(method, bestPathMtu, optimal, probes, capped = false)
    }

    private fun getInterfaceMtu(context: Context): Int = try {
        @Suppress("MissingPermission")
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("MissingPermission")
        val lp = cm.getLinkProperties(cm.activeNetwork)
        val ifaceName = lp?.interfaceName
        val mtu = if (ifaceName != null) NetworkInterface.getByName(ifaceName)?.mtu ?: 1500 else 1500
        mtu.coerceIn(1280, 9000)
    } catch (_: Exception) {
        1500
    }

    private fun execPing(host: String, size: Int, timeoutMs: Int, dontFragment: Boolean): Boolean {
        val sanitized = host.trim()
        if (sanitized.isEmpty() || sanitized.length > 253) return false
        return try {
            val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)
            val pb = if (dontFragment) {
                ProcessBuilder("ping", "-c", "1", "-s", size.toString(), "-M", "do", "-W", timeoutSec.toString(), sanitized)
            } else {
                ProcessBuilder("ping", "-c", "1", "-s", size.toString(), "-W", timeoutSec.toString(), sanitized)
            }
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val done = proc.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            if (!done) {
                proc.destroyForcibly()
                false
            } else proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}
