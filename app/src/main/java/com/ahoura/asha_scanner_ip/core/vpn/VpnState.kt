package com.ahoura.asha_scanner_ip.core.vpn

import com.ahoura.asha_scanner_ip.core.model.ProxyConfig

/**
 * Lifecycle states of the VPN client.
 */
enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR;

    val isConnected: Boolean get() = this == CONNECTED
    val isConnecting: Boolean get() = this == CONNECTING
    val isActive: Boolean get() = this == CONNECTED || this == CONNECTING
}

/**
 * A saved proxy profile for the VPN client.
 */
data class VpnProfile(
    val id: String,
    val name: String,
    val raw: String,
    val proxy: ProxyConfig,
    val cleanIp: String? = null,
    val pingMs: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    /** The actual server address to dial: the assigned clean IP or original host. */
    val targetAddress: String get() = cleanIp?.ifBlank { null } ?: proxy.address

    /** Formatted address for display. */
    val displayAddress: String
        get() = if (cleanIp != null && cleanIp != proxy.address) {
            "$cleanIp (${proxy.address})"
        } else {
            "${proxy.address}:${proxy.port}"
        }
}

/**
 * Live snapshot of VPN connection status, active profile, duration, and delay.
 * The up/down fields are a per-second rate (bytes/s), refreshed by the service
 * timer each second from the core's drained traffic counters.
 */
data class VpnStats(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val activeProfile: VpnProfile? = null,
    val connectedDurationSeconds: Long = 0L,
    val pingMs: Long? = null,
    val errorMessage: String? = null,
    val uploadBps: Long = 0L,
    val downloadBps: Long = 0L,
    val detailMessage: String? = null,
    val progress: Int = -1,
    val exitIp: String? = null,
    val country: String? = null,
    val transport: String = "wireguard",
    val tunnelMode: String = "vpn",
    val localLanIp: String? = null,
)
