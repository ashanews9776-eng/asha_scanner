package com.ahoura.asha_scanner_ip.core.net

import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Low-level TLS helpers. We dial a *specific* IP while presenting a chosen SNI
 * and Host header — this is what lets us evaluate a candidate Cloudflare edge
 * exactly as a proxy client would, and mirrors the prober in the Go original.
 */
object Tls {

    /** Well-known Cloudflare front SNIs to rotate through when none is given. */
    val DEFAULT_SNIS = listOf(
        "speed.cloudflare.com",
        "cloudflare.com",
        "www.cloudflare.com",
        "cp.cloudflare.com",
        "dash.cloudflare.com",
    )

    private val insecureContext: SSLContext by lazy {
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        SSLContext.getInstance("TLS").apply { init(null, tm, java.security.SecureRandom()) }
    }

    private val secureContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(null, null, null) }
    }

    /**
     * Open a raw TCP connection to [ip]:[port] with the given connect timeout.
     * Caller owns and must close the returned socket.
     */
    fun dial(ip: String, port: Int, connectTimeoutMs: Int): Socket {
        val socket = Socket()
        socket.tcpNoDelay = true
        // Resolve the literal directly (no DNS lookup) so dialing a scanned IP
        // never touches a resolver — faster and avoids leaking lookups.
        val addr = java.net.InetAddress.getByName(ip)
        socket.connect(InetSocketAddress(addr, port), connectTimeoutMs)
        return socket
    }

    /**
     * Upgrade an already-connected [plain] socket to TLS, presenting [sni] as the
     * SNI server name and completing the handshake within [handshakeTimeoutMs].
     */
    fun handshake(
        plain: Socket,
        ip: String,
        port: Int,
        sni: String,
        alpn: List<String>,
        insecure: Boolean,
        handshakeTimeoutMs: Int,
    ): SSLSocket {
        val ctx = if (insecure) insecureContext else secureContext
        val ssl = ctx.socketFactory.createSocket(plain, ip, port, true) as SSLSocket
        ssl.soTimeout = handshakeTimeoutMs
        ssl.useClientMode = true
        val params = ssl.sslParameters
        if (sni.isNotBlank() && !isIpLiteral(sni)) {
            params.serverNames = listOf(SNIHostName(sni))
        }
        if (alpn.isNotEmpty()) {
            params.applicationProtocols = alpn.toTypedArray()
        }
        ssl.sslParameters = params
        ssl.startHandshake()
        return ssl
    }

    private fun isIpLiteral(s: String): Boolean =
        s.isNotEmpty() && (s[0].isDigit() || s.contains(':'))
}
