package com.ahoura.asha_scanner_ip.core.ipsrc

/**
 * Cloudflare's official published IP ranges. Source: https://www.cloudflare.com/ips/
 *
 * These are the *fallback* now: the app primarily uses the finer-grained
 * `assets/cf_ipv4.txt` list (ircfspace/cf-ip-ranges, ~4.6k curated /24 blocks
 * that perform well from Iran), loaded at startup. This constant is used only if
 * that asset is missing/unreadable, and for the IPv6 ranges (no curated v6 list).
 */
object CloudflareRanges {
    val V4 = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22",
    )

    val V6 = listOf(
        "2400:cb00::/32",
        "2606:4700::/32",
        "2803:f800::/32",
        "2405:b500::/32",
        "2405:8100::/32",
        "2a06:98c0::/29",
        "2c0f:f248::/32",
    )
}
