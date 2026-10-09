package com.ahoura.asha_scanner_ip.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class GeoInfo(
    val ip: String,
    val countryCode: String,
    val countryName: String,
    val city: String?,
    val isp: String?,
    val flagEmoji: String,
    val isIran: Boolean,
)

object GeoLookup {
    private val ENDPOINTS = listOf(
        "https://api.ip.sb/geoip",
        "https://ipwho.is/",
        "https://ipapi.co/json/"
    )

    fun countryCodeToEmoji(code: String): String {
        if (code.length != 2) return "🌐"
        val upper = code.uppercase()
        val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    suspend fun lookup(socksPort: Int? = null, timeoutMs: Int = 6000): GeoInfo? = withContext(Dispatchers.IO) {
        for (endpoint in ENDPOINTS) {
            val info = runCatching { fetchEndpoint(endpoint, socksPort, timeoutMs) }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }

    private fun fetchEndpoint(endpoint: String, socksPort: Int?, timeoutMs: Int): GeoInfo? {
        val connection = if (socksPort != null) {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
            URL(endpoint).openConnection(proxy) as HttpURLConnection
        } else {
            URL(endpoint).openConnection() as HttpURLConnection
        }

        connection.apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AshaScanner")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val ip = json.optString("ip", "")
                .ifEmpty { json.optString("query", "") }
                .ifEmpty { json.optString("ip_addr", "") }
            if (ip.isBlank()) return null

            val countryCode = json.optString("country_code", "")
                .ifEmpty { json.optString("countryCode", "") }
                .ifEmpty { json.optString("country_code2", "") }
                .uppercase()

            val countryName = json.optString("country", "")
                .ifEmpty { json.optString("country_name", "") }
                .ifEmpty { countryCode }

            val city = json.optString("city", "").takeIf { it.isNotBlank() }
            val isp = json.optString("isp", "")
                .ifEmpty { json.optString("organization", "") }
                .ifEmpty { json.optString("org", "") }
                .takeIf { it.isNotBlank() }

            val isIr = countryCode == "IR" || countryName.contains("Iran", ignoreCase = true)
            val flag = countryCodeToEmoji(countryCode)

            return GeoInfo(
                ip = ip,
                countryCode = countryCode,
                countryName = countryName,
                city = city,
                isp = isp,
                flagEmoji = flag,
                isIran = isIr,
            )
        } finally {
            connection.disconnect()
        }
    }
}
