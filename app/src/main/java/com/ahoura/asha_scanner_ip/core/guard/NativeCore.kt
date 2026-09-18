package com.msnguard.vpn

import com.ahoura.asha_scanner_ip.core.guard.GuardVpnService
import org.json.JSONObject

object NativeCore {
    var isLoaded = false
        private set

    init {
        try {
            System.loadLibrary("aether")
            System.loadLibrary("aether_jni")
            isLoaded = true
        } catch (t: Throwable) {
            android.util.Log.e("NativeCore", "Failed to load native aether libraries", t)
        }
    }

    data class TunnelAddresses(
        val ipv4: String,
        val ipv6: String,
        val gatewayProxy: String = "",
        val organization: String = "",
    )

    fun prepare(config: String): TunnelAddresses {
        if (!isLoaded) return TunnelAddresses("10.0.0.1", "")
        return try {
            val code = nativePrepare(config)
            android.util.Log.i("NativeCore", "nativePrepare returned $code, lastError: ${nativeLastError()}")
            check(code == 0) { nativeLastError() }
            val result = JSONObject(nativeLastResult())
            TunnelAddresses(
                result.getString("ipv4"),
                result.optString("ipv6"),
                result.optString("gateway_proxy"),
                result.optString("organization"),
            )
        } catch (t: Throwable) {
            android.util.Log.e("NativeCore", "prepare failed: ${t.message}", t)
            TunnelAddresses("10.0.0.1", "")
        }
    }

    fun requestEmailCode(team: String, email: String) {
        if (!isLoaded) throw IllegalStateException("Native core not loaded")
        check(nativeRequestEmailCode(team, email) == 0) { nativeLastError() }
    }

    fun confirmEmailCode(code: String): String {
        if (!isLoaded) throw IllegalStateException("Native core not loaded")
        check(nativeConfirmEmailCode(code) == 0) { nativeLastError() }
        return JSONObject(nativeLastResult()).getString("token")
    }

    fun start(config: String, tunFd: Int): Int =
        if (isLoaded) {
            android.util.Log.i("NativeCore", "Starting nativeStart tunFd=$tunFd")
            val code = runCatching { nativeStart(config, tunFd) }.getOrDefault(-1)
            android.util.Log.i("NativeCore", "nativeStart returned $code, lastError: ${nativeLastError()}")
            code
        } else -1

    /**
     * Start the core with no Android TUN, exposing a local SOCKS5 listener.
     */
    fun startProxy(config: String): Int =
        if (isLoaded) {
            android.util.Log.i("NativeCore", "Starting nativeStartProxy")
            val code = runCatching { nativeStartProxy(config) }.getOrDefault(-1)
            android.util.Log.i("NativeCore", "nativeStartProxy returned $code, lastError: ${nativeLastError()}")
            code
        } else -1

    fun stop(): Int =
        if (isLoaded) runCatching { nativeStop() }.getOrDefault(0) else 0

    fun isRunning(): Boolean =
        if (isLoaded) runCatching { nativeIsRunning() }.getOrDefault(false) else false

    fun isReady(): Boolean =
        if (isLoaded) runCatching { nativeIsReady() }.getOrDefault(false) else false

    fun lastError(): String =
        if (isLoaded) runCatching { nativeLastError() }.getOrDefault("Unknown error") else "Native core not loaded"

    fun lastLog(): String =
        if (isLoaded) runCatching { nativeLastLog() }.getOrDefault("") else ""

    fun attach(service: GuardVpnService) {
        if (isLoaded) runCatching { nativeAttach(service) }
    }

    fun detach() {
        if (isLoaded) runCatching { nativeDetach() }
    }

    interface CoreCallback {
        fun onEvent(json: String)
    }

    @JvmStatic private external fun nativePrepare(config: String): Int
    @JvmStatic private external fun nativeLastResult(): String
    @JvmStatic private external fun nativeRequestEmailCode(team: String, email: String): Int
    @JvmStatic private external fun nativeConfirmEmailCode(code: String): Int
    @JvmStatic private external fun nativeStart(config: String, tunFd: Int): Int
    @JvmStatic private external fun nativeStartProxy(config: String): Int
    @JvmStatic private external fun nativeStop(): Int
    @JvmStatic private external fun nativeIsRunning(): Boolean
    @JvmStatic private external fun nativeIsReady(): Boolean
    @JvmStatic private external fun nativeLastError(): String
    @JvmStatic private external fun nativeLastLog(): String
    @JvmStatic private external fun nativeAttach(service: GuardVpnService)
    @JvmStatic private external fun nativeDetach()
}
