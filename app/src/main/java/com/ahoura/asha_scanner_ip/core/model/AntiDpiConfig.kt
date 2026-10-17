package com.ahoura.asha_scanner_ip.core.model

import org.json.JSONObject

/**
 * Anti-DPI configuration and fragmentation recipe.
 *
 * Slices the initial TLS ClientHello flight so that deep packet inspection
 * middleboxes cannot reconstruct and match the SNI in a single packet.
 * Defaults mirror the published values from t.me/MatinSenPaii/5469.
 */
data class AntiDpiConfig(
    val enabled: Boolean = true,
    val finalmask: String = DEFAULT_FINALMASK,
    val fingerprint: String = "unsafe",
    val alpn: String = "http/1.1",
    val cipherSuites: String = DEFAULT_CIPHERS,
) {
    companion object {
        const val DEFAULT_FINALMASK =
            """{"tcp": [{"type": "fragment", "settings": {"packets": "tlshello", "lengths": ["0", "104", "1"], "delays": ["0"], "maxSplit": "0"}},{"type": "fragment", "settings": {"packets": "1-1", "lengths": ["114", "1"], "delays": ["1"], "maxSplit": "11"}}]}"""

        const val DEFAULT_CIPHERS =
            "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256:" +
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384:" +
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:" +
                "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256:" +
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA:TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA"

        fun suggestedValues(): AntiDpiConfig = AntiDpiConfig()
    }

    /**
     * Validates that the recipe JSON is parseable and non-empty.
     * Returns null if valid, or an error message if invalid.
     */
    fun validate(): String? {
        if (!enabled) return null
        if (finalmask.isBlank()) return "Anti-DPI finalmask recipe cannot be empty"
        return runCatching {
            val obj = JSONObject(finalmask)
            if (!obj.has("tcp")) return "Missing 'tcp' section in finalmask recipe"
            null
        }.getOrElse { "Invalid JSON in finalmask: ${it.message}" }
    }

    /**
     * Parses the fragmentation recipe parameters from the finalmask JSON.
     */
    fun parseRecipe(): AntiDpiRecipe {
        if (!enabled || finalmask.isBlank()) return AntiDpiRecipe()
        return runCatching {
            val root = JSONObject(finalmask)
            val tcpArray = root.optJSONArray("tcp") ?: return AntiDpiRecipe()
            var first = 104
            var next = 114
            var delay = 1L
            var maxSplit = 11

            for (i in 0 until tcpArray.length()) {
                val item = tcpArray.optJSONObject(i) ?: continue
                val settings = item.optJSONObject("settings") ?: continue
                val packets = settings.optString("packets", "")
                val lengths = settings.optJSONArray("lengths")
                val delays = settings.optJSONArray("delays")
                val ms = settings.optString("maxSplit", "")

                if (packets.contains("tlshello", ignoreCase = true)) {
                    if (lengths != null) {
                        for (j in 0 until lengths.length()) {
                            val v = lengths.optString(j).toIntOrNull() ?: 0
                            if (v > 0) {
                                first = v
                                break
                            }
                        }
                    }
                } else if (packets.contains("1-1") || packets.contains("first", ignoreCase = true)) {
                    if (lengths != null) {
                        for (j in 0 until lengths.length()) {
                            val v = lengths.optString(j).toIntOrNull() ?: 0
                            if (v > 0) {
                                next = v
                                break
                            }
                        }
                    }
                    if (delays != null && delays.length() > 0) {
                        val d = delays.optString(0).toLongOrNull() ?: 1L
                        if (d >= 0) delay = d
                    }
                    val msVal = ms.toIntOrNull() ?: 0
                    if (msVal > 0) maxSplit = msVal
                }
            }
            AntiDpiRecipe(firstChunk = first, nextChunk = next, delayMs = delay, maxSplit = maxSplit)
        }.getOrDefault(AntiDpiRecipe())
    }
}

data class AntiDpiRecipe(
    val firstChunk: Int = 104,
    val nextChunk: Int = 114,
    val delayMs: Long = 1L,
    val maxSplit: Int = 11,
)
