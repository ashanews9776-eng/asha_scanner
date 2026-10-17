package com.ahoura.asha_scanner_ip.core.guard

import org.json.JSONObject
import java.util.Base64

enum class TlsFragmentMode(val wireName: String) {
    AUTOMATIC("auto"),
    RECORDS("records"),
    COMPACT("compact"),
    CUSTOM("custom"),
}

/**
 * Advanced TLS Handshake Fragmentation and ECH configuration for MASQUE and WOM tunnels.
 */
data class TlsOptions(
    val fragmentMode: TlsFragmentMode = TlsFragmentMode.AUTOMATIC,
    val fragmentSize: String = "16-32",
    val fragmentDelay: String = "0-1",
    val splitSni: Boolean = true,
    val customEch: Boolean = false,
    val echConfigList: String = "",
) {
    fun validationError(fragmentEnabled: Boolean, echEnabled: Boolean): String? = when {
        fragmentEnabled && fragmentMode == TlsFragmentMode.CUSTOM &&
            !validRange(fragmentSize, 1, 16384) -> "Invalid fragment size range (1..16384)"
        fragmentEnabled && fragmentMode == TlsFragmentMode.CUSTOM &&
            !validRange(fragmentDelay, 0, 100) -> "Invalid fragment delay range (0..100 ms)"
        echEnabled && customEch && !validEchConfigList(echConfigList) -> "Invalid ECH Config List (Base64 expected)"
        else -> null
    }

    fun applyTo(json: JSONObject) {
        val fragmentEnabled = json.optBoolean("fragmentTls", true)
        json.put("fragmentMode", if (fragmentEnabled) fragmentMode.wireName else "auto")
        json.put(
            "helloShape",
            if (fragmentEnabled && fragmentMode in setOf(TlsFragmentMode.RECORDS, TlsFragmentMode.COMPACT)) {
                fragmentMode.wireName
            } else {
                ""
            }
        )
        json.put(
            "fragmentSize",
            if (fragmentEnabled && fragmentMode == TlsFragmentMode.CUSTOM) fragmentSize.trim() else ""
        )
        json.put(
            "fragmentDelay",
            if (fragmentEnabled && fragmentMode == TlsFragmentMode.CUSTOM) fragmentDelay.trim() else ""
        )
        json.put("fragmentSni", splitSni)
        val echText = echConfigList.filterNot(Char::isWhitespace)
        val canonicalEch = runCatching {
            Base64.getEncoder().encodeToString(Base64.getDecoder().decode(echText))
        }.getOrDefault(echText)
        val echActive = json.optBoolean("encryptedHello") && customEch
        json.put("echConfigList", if (echActive) canonicalEch else "")
        json.put("customEch", echActive)
    }

    fun encode(): String = JSONObject()
        .put("fragmentMode", fragmentMode.wireName)
        .put("fragmentSize", fragmentSize)
        .put("fragmentDelay", fragmentDelay)
        .put("splitSni", splitSni)
        .put("customEch", customEch)
        .put("echConfigList", echConfigList)
        .toString()

    companion object {
        fun decode(raw: String?): TlsOptions = runCatching {
            val json = JSONObject(raw ?: "{}")
            TlsOptions(
                fragmentMode = TlsFragmentMode.entries.firstOrNull { it.wireName == json.optString("fragmentMode") }
                    ?: TlsFragmentMode.AUTOMATIC,
                fragmentSize = json.optString("fragmentSize", "16-32"),
                fragmentDelay = json.optString("fragmentDelay", "0-1"),
                splitSni = json.optBoolean("splitSni", true),
                customEch = json.optBoolean("customEch", false),
                echConfigList = json.optString("echConfigList", ""),
            )
        }.getOrDefault(TlsOptions())

        fun validRange(raw: String, minimum: Int, maximum: Int): Boolean {
            val values = raw.trim().split('-')
            if (values.size !in 1..2) return false
            val low = values.first().toIntOrNull() ?: return false
            val high = values.last().toIntOrNull() ?: return false
            return low in minimum..maximum && high in low..maximum
        }

        fun validEchConfigList(raw: String): Boolean = runCatching {
            val bytes = Base64.getDecoder().decode(raw.filterNot(Char::isWhitespace))
            fun u16(offset: Int) = ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
            if (bytes.size < 7 || bytes.size > 65537 || u16(0) != bytes.size - 2) return false
            var offset = 2
            while (offset < bytes.size) {
                if (offset + 4 > bytes.size) return false
                val length = u16(offset + 2)
                if (length == 0) return false
                offset += 4 + length
            }
            offset == bytes.size
        }.getOrDefault(false)
    }
}
