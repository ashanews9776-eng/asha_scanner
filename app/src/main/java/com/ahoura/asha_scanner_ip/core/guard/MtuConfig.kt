package com.ahoura.asha_scanner_ip.core.guard

import android.content.Context

/**
 * Per-method MTU (Maximum Transmission Unit).
 *
 * One value per method: MASQUE / WireGuard / WoW / Psiphon / Tor / SHARD.
 *
 * Defaults:
 * - MASQUE 1304, WireGuard 1440, WoW 1280
 * - Psiphon 1500, Tor 1500
 * - SHARD 1500 (PattNG parity — AppConfig.VPN_MTU = 1500)
 */
object MtuConfig {

    const val MIN_MTU = 68
    const val MAX_MTU = 1500

    const val KEY_MASQUE = "mtu_masque"
    const val KEY_WIREGUARD = "mtu_wireguard"
    const val KEY_WOW = "mtu_wow"
    const val KEY_PSIPHON = "mtu_psiphon"
    const val KEY_TOR = "mtu_tor"
    const val KEY_SHARD = "mtu_shard"

    const val DEFAULT_MASQUE = 1304
    const val DEFAULT_WIREGUARD = 1440
    const val DEFAULT_WOW = 1280
    const val DEFAULT_PSIPHON = 1500
    const val DEFAULT_TOR = 1500
    const val DEFAULT_SHARD = 1500

    enum class Method(
        val prefKey: String,
        val default: Int,
        val title: String,
    ) {
        MASQUE(KEY_MASQUE, DEFAULT_MASQUE, "MASQUE"),
        WIREGUARD(KEY_WIREGUARD, DEFAULT_WIREGUARD, "WireGuard"),
        WOW(KEY_WOW, DEFAULT_WOW, "WOW"),
        PSIPHON(KEY_PSIPHON, DEFAULT_PSIPHON, "Psiphon"),
        TOR(KEY_TOR, DEFAULT_TOR, "Tor"),
        SHARD(KEY_SHARD, DEFAULT_SHARD, "SHARD"),
    }

    fun isValid(v: Int): Boolean = v in MIN_MTU..MAX_MTU

    fun rejection(v: Int): String? = when {
        v < MIN_MTU -> "Minimum is $MIN_MTU"
        v > MAX_MTU -> "Maximum is $MAX_MTU"
        else -> null
    }

    fun get(context: Context, method: Method): Int {
        val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(method.prefKey, -1)
        return if (raw == -1 || raw == 0) method.default else raw.coerceIn(MIN_MTU, MAX_MTU)
    }

    fun isCustom(context: Context, method: Method): Boolean {
        val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(method.prefKey, -1)
        return raw != -1 && raw != 0
    }

    fun set(context: Context, method: Method, value: Int): Boolean {
        if (!isValid(value)) return false
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt(method.prefKey, value).apply()
        return true
    }

    fun reset(context: Context, method: Method) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove(method.prefKey).apply()
    }

    fun displayValue(context: Context, method: Method): String {
        val v = get(context, method)
        return if (isCustom(context, method)) "$v" else "$v (default)"
    }

    fun forWarpProtocol(context: Context, protocolUpper: String): Int = when {
        protocolUpper.contains("MASQUE") || protocolUpper.contains("MIM") -> get(context, Method.MASQUE)
        protocolUpper.contains("WIREGUARD") -> get(context, Method.WIREGUARD)
        protocolUpper.contains("GOOL") || protocolUpper.contains("WOW") -> get(context, Method.WOW)
        else -> get(context, Method.WIREGUARD)
    }
}
