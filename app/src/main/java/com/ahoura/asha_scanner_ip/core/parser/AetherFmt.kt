package com.ahoura.asha_scanner_ip.core.parser

import android.net.Uri
import com.ahoura.asha_scanner_ip.core.aether.AetherCliSettings
import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Serializes and deserializes `aether://` URI configuration links,
 * fully interoperable with PattNG, v2rayNG (Aether edition), and Asha Scanner.
 */
object AetherFmt {

    fun parse(raw: String): ProxyConfig {
        val trimmed = raw.trim()
        val withoutScheme = when {
            trimmed.startsWith("aether://", ignoreCase = true) -> trimmed.substring(9)
            trimmed.startsWith("warp://", ignoreCase = true) -> trimmed.substring(7)
            else -> throw IllegalArgumentException("Not an aether:// link")
        }

        val remark = if (withoutScheme.contains("#")) {
            decode(withoutScheme.substringAfter("#"))
        } else {
            "Aether"
        }

        val mainPart = withoutScheme.substringBefore("#")
        val endpointPart = mainPart.substringBefore("?")
        val queryPart = if (mainPart.contains("?")) mainPart.substringAfter("?") else ""

        val queryMap = mutableMapOf<String, String>()
        if (queryPart.isNotBlank()) {
            queryPart.split("&").forEach { pair ->
                val eq = pair.indexOf("=")
                if (eq > 0) {
                    val key = pair.substring(0, eq).trim()
                    val value = decode(pair.substring(eq + 1).trim())
                    queryMap[key] = value
                }
            }
        }

        var host = ""
        var port = 0
        if (endpointPart.isNotBlank()) {
            val cleanEp = endpointPart.removePrefix("[").removeSuffix("]")
            if (cleanEp.contains(":")) {
                val lastColon = cleanEp.lastIndexOf(":")
                host = cleanEp.substring(0, lastColon).trim()
                port = cleanEp.substring(lastColon + 1).toIntOrNull() ?: 0
            } else {
                host = cleanEp.trim()
            }
        }

        val protocol = queryMap["protocol"] ?: "wg"
        val transport = queryMap["transport"] ?: "h3"
        val sni = queryMap["sni"] ?: "www.cloudflare.com"
        val scan = queryMap["scan"] ?: "balanced"
        val fragment = queryMap["fragment"] == "1"
        val ech = queryMap["ech"] == "1"

        return ProxyConfig(
            protocol = Protocol.AETHER,
            address = host,
            port = port,
            encryption = protocol, // holds wireguard | masque | wom | gool | mim
            network = transport,   // holds h3 | h2
            sni = sni,
            security = if (ech) "ech" else "tls",
            path = scan,           // holds scan mode
            remark = remark.ifBlank { "Aether - ${protocol.uppercase()}" },
            raw = trimmed,
        )
    }

    fun toUri(
        name: String,
        protocol: String = "masque",
        endpoint: String = "",
        scanMode: String = "balanced",
        transport: String = "h3",
        sni: String = "www.cloudflare.com",
        fragment: Boolean = false,
        ech: Boolean = false,
    ): String {
        val query = mutableListOf<String>()
        val protoNorm = when (protocol.lowercase()) {
            "wireguard", "wg" -> "wg"
            "masque" -> "masque"
            "wom", "wg-over-masque" -> "wg-over-masque"
            "gool", "warp-in-warp" -> "gool"
            "mim", "masque-in-masque" -> "mim"
            else -> protocol.lowercase()
        }
        query += "protocol=${encode(protoNorm)}"
        query += "scan=${encode(scanMode)}"

        if (protoNorm in listOf("masque", "mim", "wg-over-masque", "wom")) {
            query += "transport=${encode(transport)}"
            if (sni.isNotBlank() && sni != "www.cloudflare.com") {
                query += "sni=${encode(sni)}"
            }
            if (fragment) query += "fragment=1"
            if (ech) query += "ech=1"
        }

        val queryStr = query.joinToString("&")
        val epStr = endpoint.trim()
        val label = encode(name.ifBlank { "Aether ${protoNorm.uppercase()}" })

        return if (epStr.isNotBlank()) {
            "aether://$epStr?$queryStr#$label"
        } else {
            "aether://?$queryStr#$label"
        }
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    private fun encode(s: String): String = runCatching { URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)
}
