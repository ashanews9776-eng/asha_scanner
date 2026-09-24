package com.ahoura.asha_scanner_ip.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

enum class Lang(val code: String) { FA("fa"), EN("en") }

/**
 * All user-facing prose for the current terminal UI, resolved per language so the
 * in-app FA/EN toggle is instant. Short technical tokens (HTTP/TLS/TCP, port
 * numbers, metric column codes like MS/DL/COLO, units) are intentionally kept
 * identical across languages — they read the same to Persian proxy users and
 * keep the cyberpunk look consistent.
 */
interface AppStrings {
    // Home
    val homeSubtitle: String
    val quickScan: String
    val quickScanDesc: String
    val customScan: String
    val customScanDesc: String
    val testIps: String
    val testIpsDesc: String
    val discoverColos: String
    val discoverColosDesc: String
    val telegramChannel: String
    val join: String
    val cfRanges: String
    val cidrs: String
    val license: String
    // Scan settings
    val count: String
    val workers: String
    val timeout: String
    val port: String
    val advancedOptions: String
    val mode: String
    val speedTest: String
    val openSiteFallback: String
    val openSiteFallbackHint: String
    val configOptional: String
    val configPlaceholder: String
    val configParsed: String
    val configUnrecognized: String
    val startScan: String
    // Live scan
    val measuringSpeed: String
    val stabilityCheck: String
    val resolvingOpenSites: String
    val probing: String
    val cancel: String
    val fallbackBanner: String
    val healthy: String
    val probed: String
    val done: String
    val tested: String
    val elapsed: String
    val phaseHandshake: String
    val phaseThroughConfig: String
    val phaseEdgesMeasured: String
    val phaseDnsLookup: String
    val phaseFallbackEdges: String
    val radar: String
    val copyIp: String
    // Update
    val updateAvailable: String
    val updateDesc: String
    val downloadNow: String
    val later: String
    val localSub: String
    val localSubDesc: String
    val smartStop: String
    val smartStopDesc: String
    val fragment: String
    val fragmentDesc: String
    val loadIsp: String
    val mci: String
    val irancell: String
    val mokhaberat: String
    val rightel: String
    val gaming: String
    val streaming: String
    val stability: String
    val gradeS: String
    val gradeA: String
    val gradeB: String
    val gradeC: String
    val gradeLegend: String
    val gamingDesc: String
    val streamingDesc: String
    val stabilityDesc: String
    // Tiers
    val tierTurbo: String
    val tierTurboDesc: String
    val tierBalanced: String
    val tierBalancedDesc: String
    val tierThorough: String
    val tierThoroughDesc: String
    val tierStealth: String
    val tierStealthDesc: String
    val tierIronclad: String
    val tierIroncladDesc: String
    val tierCustom: String
    val tierCustomDesc: String
    // Results
    val results: String
    val healthyIpsTopShown: String
    val bestMs: String
    val copyConfigs: String
    val share: String
    val copy: String
    val scanAgain: String
    val noResults: String
    val noResultsHint: String
    val copiedConfigs: String
    val copiedIps: String
    val copiedConfigFor: String
    val copiedIp: String
    // Test IPs
    val pasteIps: String
    val loadFile: String
    val fixedSettings: String
    val startTest: String
    val ipsSuffix: String
    // Discover
    val popDiscovery: String
    val popDiscoveryDesc: String
    val specs: String
    val startDiscovery: String
    // About
    val about: String
    val aboutBlurb: String
    val telegram: String
    // VPN Mode
    val vpnMode: String
    val vpnModeDesc: String
    val connect: String
    val disconnect: String
    val connecting: String
    val connected: String
    val disconnected: String
    val activeConfig: String
    val noActiveConfig: String
    val tapToSelect: String
    val testDelay: String
    val testingDelay: String
    val delayMs: String
    val cleanIpActive: String
    val directHost: String
    val cleanIpOption: String
    val cleanIpOptionDesc: String
    val manageConfigs: String
    val importConfig: String
    val pasteConfigPrompt: String
    val saveConfig: String
    val deleteConfig: String
    val select: String
    val connectWithCleanIp: String
    val vpnPermissionRequired: String
    val connectedDuration: String
    val vpnBypassIr: String
    val vpnBypassIrDesc: String
    val dnsTool: String
    val dnsToolDesc: String
    val dnsInfo: String
    val dnsTestAll: String
    val dnsTesting: String
    val dnsFastest: String
    val dnsUseFastest: String
    val dnsApplied: String
    val dnsNoAnswer: String
    val dnsStart: String
    val dnsStop: String
    val dnsResume: String
    val dnsWorkers: String
    val dnsTotal: String
    val dnsValid: String
    val dnsRejected: String
    val dnsProgress: String
    val dnsCatAll: String
    val dnsCatAntiSanction: String
    val dnsCatGlobal: String
    val dnsCatCustom: String
    val dnsAddCustom: String
    val dnsCustomName: String
    val dnsCustomIp: String
    val dnsSave: String
    val dnsImport: String
    val dnsExport: String
    val dnsImportPrompt: String
    val dnsCopiedValid: String
    val dnsDelete: String
    val dnsTargetDomain: String
    val dnsWarningHighWorkers: String
    val dnsSearchHint: String
    // StormDNS Auto-Tune (measured MTU profiles)
    val stormTune: String
    val stormTuneDesc: String
    val stormTuneRun: String
    val stormTuneStop: String
    val stormTuneAggressive: String
    val stormTuneApplied: String
    val stormTuneNeedProfile: String
    val stormTuneNeedsIdle: String
    val stormTuneActive: String
    // Asha Guard Anti-Censorship Transports & Modes
    val transportWireguard: String
    val transportWireguardDesc: String
    val transportMasque: String
    val transportMasqueDesc: String
    val transportGool: String
    val transportGoolDesc: String
    val transportPsiphon: String
    val transportPsiphonDesc: String
    val transportTor: String
    val transportTorDesc: String
    val transportShard: String
    val transportShardDesc: String
    val transportCustom: String
    val transportCustomDesc: String
    val psiphonOverWarp: String
    val psiphonOverWarpDesc: String
    val torOverWarp: String
    val torOverWarpDesc: String
    val exitCountry: String
    val autoCountry: String
    val torBridgeMode: String
    val torBridgeAuto: String
    val torBridgeDirect: String
    val torBridgeMeek: String
    val torBridgeObfs4: String
    val torBridgeSnowflake: String
    val outerTransport: String
    val outerTransportDesc: String
    val outerAuto: String
    val tunnelType: String
    val tunnelTypeVpn: String
    val tunnelTypeVpnDesc: String
    val tunnelTypeProxy: String
    val tunnelTypeProxyDesc: String
    val lanSharing: String
    val lanSharingDesc: String
    val proxyPort: String
    val httpProxyPort: String
    val localIpAddress: String
    val killSwitch: String
    val killSwitchDesc: String
    val smartSplit: String
    val smartSplitDesc: String
    val exitIpLabel: String
    val countryLabel: String
    val bootstrapLabel: String
    val speedLabel: String
    val connectWarp: String
    val connectMasque: String
    val connectCustom: String
    val useBestCleanIpWarp: String
    val zeroTrustTitle: String
    val zeroTrustDesc: String
    val zeroTrustTeam: String
    val zeroTrustEmail: String
    val zeroTrustCode: String
    val zeroTrustSendCode: String
    val zeroTrustVerify: String
    val zeroTrustServiceToken: String
    val zeroTrustClientId: String
    val zeroTrustClientSecret: String
    val zeroTrustGateway: String
    val zeroTrustGatewayDesc: String
    val zeroTrustEnrolled: String
    val zeroTrustClear: String
    val zeroTrustOtpMode: String
    val upstreamProxy: String
    val upstreamProxyDesc: String
    val upstreamProxyPlaceholder: String
    val wiwOuter: String
    val wiwInner: String
    val wiwDesc: String
    val pinAsWarpEndpoint: String
    val endpointPinnedSuccess: String
    val cipherSuites: String
    val cipherSuitesPlaceholder: String
    val dialMode: String
    val dialModeAuto: String
    val dialModeIpv4: String
    val dialModeIpv6: String
    val unsafeFingerprint: String
}


internal object EN : AppStrings {
    override val homeSubtitle: String = "CLOUDFLARE IP SCANNER"
    override val quickScan: String = "Quick Scan"
    override val quickScanDesc: String = "Scan random CF IPs"
    override val customScan: String = "Custom Scan"
    override val customScanDesc: String = "Configure details"
    override val testIps: String = "Test IPs"
    override val testIpsDesc: String = "Validate a list"
    override val discoverColos: String = "Discover Colos"
    override val discoverColosDesc: String = "Find reachable DCs"
    override val telegramChannel: String = "TELEGRAM CHANNEL"
    override val join: String = "JOIN →"
    override val cfRanges: String = "CF RANGES:"
    override val cidrs: String = "CIDRs"
    override val license: String = "MIT LICENSE"
    override val count: String = "Count"
    override val workers: String = "Workers"
    override val timeout: String = "Timeout"
    override val port: String = "Port"
    override val advancedOptions: String = "Advanced options"
    override val mode: String = "Mode"
    override val speedTest: String = "Speed test"
    override val openSiteFallback: String = "Open-site fallback"
    override val openSiteFallbackHint: String = "If no range IP responds, resolve open Cloudflare sites and probe their IPs"
    override val configOptional: String = "Config (optional)"
    override val configPlaceholder: String = "vless:// or trojan:// for ready configs"
    override val configParsed: String = "PARSED · SPEED TEST RUNS THROUGH THIS CONFIG"
    override val configUnrecognized: String = "✕ UNRECOGNIZED LINK · EXPECTED vless:// OR trojan://"
    override val startScan: String = "Start Scan"
    override val measuringSpeed: String = "MEASURING SPEED"
    override val stabilityCheck: String = "STABILITY RE-CHECK"
    override val resolvingOpenSites: String = "RESOLVING OPEN SITES"
    override val probing: String = "PROBING"
    override val cancel: String = "CANCEL"
    override val fallbackBanner: String = "No range IP responded — trying IPs of open Cloudflare sites"
    override val healthy: String = "HEALTHY"
    override val probed: String = "PROBED"
    override val done: String = "DONE"
    override val tested: String = "TESTED"
    override val elapsed: String = "ELAPSED"
    override val phaseHandshake: String = "PHASE 1/2 · HANDSHAKE PROBE"
    override val phaseThroughConfig: String = "THROUGH YOUR CONFIG"
    override val phaseEdgesMeasured: String = "EDGES MEASURED"
    override val phaseDnsLookup: String = "FALLBACK · DNS LOOKUP OF OPEN CF SITES"
    override val phaseFallbackEdges: String = "FALLBACK · PROBING OPEN CF SITE EDGES"
    override val radar: String = "RADAR"
    override val copyIp: String = "COPY IP"
    override val updateAvailable: String = "Update Available"
    override val updateDesc: String = "A new version (%s) is available on GitHub. Would you like to download it now?"
    override val downloadNow: String = "DOWNLOAD NOW"
    override val later: String = "LATER"
    override val localSub: String = "LOCAL SUB LINK"
    override val localSubDesc: String = "Copy this link to v2rayNG/NekoBox to auto-sync results."
    override val smartStop: String = "SMART STOP"
    override val smartStopDesc: String = "Stop scanning once enough healthy IPs are found."
    override val fragment: String = "TLS FRAGMENTATION"
    override val fragmentDesc: String = "Split packets to bypass strict SNI blocking."
    override val loadIsp: String = "LOAD ISP RANGES"
    override val mci: String = "MCI (Hamrah-e Aval)"
    override val irancell: String = "Irancell"
    override val mokhaberat: String = "Mokhaberat"
    override val rightel: String = "Rightel"
    override val gaming: String = "GAMING"
    override val streaming: String = "STREAMING"
    override val stability: String = "STABILITY"
    override val gradeS: String = "Excellent (Ping < 80ms, Speed > 20Mbps)"
    override val gradeA: String = "Great (Ping < 120ms, Speed > 10Mbps)"
    override val gradeB: String = "Good (Ping < 180ms, Speed > 3Mbps)"
    override val gradeC: String = "Fair (Other healthy IPs)"
    override val results: String = "Results"
    override val healthyIpsTopShown: String = "HEALTHY IPs · TOP %d SHOWN"
    override val bestMs: String = "BEST MS"
    override val copyConfigs: String = "COPY %d CONFIGS"
    override val share: String = "SHARE"
    override val copy: String = "COPY"
    override val scanAgain: String = "Scan Again"
    override val noResults: String = "NO WORKING IPs FOUND"
    override val noResultsHint: String = "Try more IPs, another port, or a longer timeout"
    override val copiedConfigs: String = "Copied %d ready configs"
    override val copiedIps: String = "Copied %d IPs"
    override val copiedConfigFor: String = "Copied config for %s"
    override val copiedIp: String = "Copied: %s"
    override val pasteIps: String = "Paste IPs — one per line or CSV"
    override val loadFile: String = "Load .txt / .csv"
    override val fixedSettings: String = "Fixed settings"
    override val startTest: String = "Start Test"
    override val ipsSuffix: String = "IPs"
    override val popDiscovery: String = "POP DISCOVERY"
    override val popDiscoveryDesc: String = "Probes 300 random IPv4 IPs via HTTP and groups results by Cloudflare PoP (colo). " +
        "Useful to see which data centers are reachable from your network right now."
    override val specs: String = "Specs"
    override val startDiscovery: String = "Start Discovery"
    override val about: String = "About"
    override val aboutBlurb: String = "A native Cloudflare clean-IP scanner for restricted networks — finds fast, " +
        "reachable CF edges for VLESS / Trojan configs."
    override val telegram: String = "Telegram channel"
    override val gradeLegend: String = "QUALITY RANKING GUIDE"
    override val gamingDesc: String = "Low jitter & 0% packet loss"
    override val streamingDesc: String = "High bandwidth (15Mbps+)"
    override val stabilityDesc: String = "Reliable for long sessions"
    override val tierTurbo: String = "TURBO"
    override val tierTurboDesc: String = "Fastest discovery, TCP only"
    override val tierBalanced: String = "BALANCED"
    override val tierBalancedDesc: String = "Default optimized profile"
    override val tierThorough: String = "THOROUGH"
    override val tierThoroughDesc: String = "Deeper TLS verification"
    override val tierStealth: String = "STEALTH"
    override val tierStealthDesc: String = "Slow and careful for strict DPI"
    override val tierIronclad: String = "IRONCLAD"
    override val tierIroncladDesc: String = "Maximum reliability, full HTTP"
    override val tierCustom: String = "CUSTOM"
    override val tierCustomDesc: String = "Manual adjustments"
    // VPN Mode
    override val vpnMode: String = "VPN Client"
    override val vpnModeDesc: String = "Connect directly with proxy"
    override val connect: String = "CONNECT"
    override val disconnect: String = "DISCONNECT"
    override val connecting: String = "CONNECTING..."
    override val connected: String = "CONNECTED"
    override val disconnected: String = "DISCONNECTED"
    override val activeConfig: String = "ACTIVE PROFILE"
    override val noActiveConfig: String = "No active proxy profile"
    override val tapToSelect: String = "Tap to select or import config"
    override val testDelay: String = "PING"
    override val testingDelay: String = "..."
    override val delayMs: String = "ms"
    override val cleanIpActive: String = "CLEAN IP ATTACHED"
    override val directHost: String = "ORIGINAL HOST"
    override val cleanIpOption: String = "Attach Clean IP"
    override val cleanIpOptionDesc: String = "Tunnel through a fast Cloudflare edge IP"
    override val manageConfigs: String = "Config Profiles"
    override val importConfig: String = "IMPORT CONFIG"
    override val pasteConfigPrompt: String = "Paste vless://, trojan://, or stormdns:// share link"
    override val saveConfig: String = "SAVE CONFIG"
    override val deleteConfig: String = "Delete"
    override val select: String = "Select"
    override val connectWithCleanIp: String = "Connect with this Clean IP"
    override val vpnPermissionRequired: String = "VPN permission is required to start the tunnel"
    override val connectedDuration: String = "DURATION"
    override val vpnBypassIr: String = "Bypass Iranian Sites"
    override val vpnBypassIrDesc: String = "Local banks, government and .ir services connect directly instead of through the tunnel"
    override val dnsTool: String = "DNS Tuner"
    override val dnsToolDesc: String = "Find & apply fastest DNS"
    override val dnsInfo: String = "Probe DNS resolvers on your network and apply the fastest one to the VPN tunnel. Anti-sanction resolvers also unblock sanctioned sites for direct traffic. Takes effect on next connect."
    override val dnsTestAll: String = "TEST ALL RESOLVERS"
    override val dnsTesting: String = "TESTING"
    override val dnsFastest: String = "FASTEST"
    override val dnsUseFastest: String = "USE FASTEST"
    override val dnsApplied: String = "DNS set. Reconnect the VPN to switch."
    override val dnsNoAnswer: String = "no answer"
    override val dnsStart: String = "START"
    override val dnsStop: String = "STOP"
    override val dnsResume: String = "RESUME"
    override val dnsWorkers: String = "Workers"
    override val dnsTotal: String = "Total"
    override val dnsValid: String = "Valid"
    override val dnsRejected: String = "Rejected"
    override val dnsProgress: String = "Progress"
    override val dnsCatAll: String = "ALL"
    override val dnsCatAntiSanction: String = "ANTI-SANCTION"
    override val dnsCatGlobal: String = "GLOBAL"
    override val dnsCatCustom: String = "CUSTOM"
    override val dnsAddCustom: String = "Add Custom DNS"
    override val dnsCustomName: String = "Resolver Name"
    override val dnsCustomIp: String = "IP Address"
    override val dnsSave: String = "Save Resolver"
    override val dnsImport: String = "Import List"
    override val dnsExport: String = "Export Valid"
    override val dnsImportPrompt: String = "Paste lines of Category|Name|IP, Name|IP, or bare IP"
    override val dnsCopiedValid: String = "Working resolvers copied to clipboard"
    override val dnsDelete: String = "Delete"
    override val dnsTargetDomain: String = "Probe Target"
    override val dnsWarningHighWorkers: String = "High worker count may increase network jitter."
    override val dnsSearchHint: String = "Search name or IP..."
    override val stormTune: String = "StormDNS Auto-Tune"
    override val stormTuneDesc: String = "Tests measured MTU profiles on your carrier and keeps the fastest one for the DNS tunnel. Needs an active StormDNS profile; run while the VPN is off."
    override val stormTuneRun: String = "RUN AUTO-TUNE"
    override val stormTuneStop: String = "STOP"
    override val stormTuneAggressive: String = "Include aggressive"
    override val stormTuneApplied: String = "Applied. Reconnect the DNS tunnel to switch."
    override val stormTuneNeedProfile: String = "Select a StormDNS / CottenDNS profile first"
    override val stormTuneNeedsIdle: String = "Disconnect the VPN before running auto-tune"
    override val stormTuneActive: String = "ACTIVE PRESET"
    // Asha Guard Anti-Censorship Transports & Modes
    override val transportWireguard: String = "WireGuard"
    override val transportWireguardDesc: String = "Fast direct UDP Noise protocol"
    override val transportMasque: String = "MASQUE"
    override val transportMasqueDesc: String = "CONNECT-IP over HTTP/3 & HTTP/2"
    override val transportGool: String = "WARP-on-WARP"
    override val transportGoolDesc: String = "Double-layer WireGuard tunnel"
    override val transportPsiphon: String = "Psiphon"
    override val transportPsiphonDesc: String = "Multi-protocol ladder · 25 countries"
    override val transportTor: String = "Tor"
    override val transportTorDesc: String = "Onion routing & Lyrebird bridges"
    override val transportShard: String = "SHARD"
    override val transportShardDesc: String = "Cloudflare edge pool & TLS fragment"
    override val transportCustom: String = "Custom Proxy"
    override val transportCustomDesc: String = "VLESS / Trojan / StormDNS + Clean IP"
    override val psiphonOverWarp: String = "Psiphon over WARP"
    override val psiphonOverWarpDesc: String = "Tunnel Psiphon through Cloudflare WARP to bypass carrier blocks"
    override val torOverWarp: String = "Tor over WARP"
    override val torOverWarpDesc: String = "Tunnel Tor bootstrap through Cloudflare WARP"
    override val exitCountry: String = "Exit Country"
    override val autoCountry: String = "Auto (Fastest)"
    override val torBridgeMode: String = "Tor Bridge"
    override val torBridgeAuto: String = "Auto (Direct → Meek → obfs4 → Snowflake)"
    override val torBridgeDirect: String = "Direct (No bridge)"
    override val torBridgeMeek: String = "Meek (CDN-fronted)"
    override val torBridgeObfs4: String = "obfs4 (Scrambled)"
    override val torBridgeSnowflake: String = "Snowflake (WebRTC)"
    override val outerTransport: String = "WARP Outer Transport"
    override val outerTransportDesc: String = "Underlying transport for chained tunnels"
    override val outerAuto: String = "Auto (MASQUE → WireGuard → WoW)"
    override val tunnelType: String = "Tunnel Mode"
    override val tunnelTypeVpn: String = "Whole-Device VPN"
    override val tunnelTypeVpnDesc: String = "Routes all device traffic through tunnel"
    override val tunnelTypeProxy: String = "Local SOCKS5 / HTTP Proxy"
    override val tunnelTypeProxyDesc: String = "No VPN permission required. Point Telegram, browser, or PC to local port."
    override val lanSharing: String = "Share over LAN / Hotspot"
    override val lanSharingDesc: String = "Listen on 0.0.0.0 so other devices on Wi-Fi/Hotspot can use this tunnel"
    override val proxyPort: String = "SOCKS5 Port"
    override val httpProxyPort: String = "HTTP Proxy Port"
    override val localIpAddress: String = "Local Endpoint"
    override val killSwitch: String = "Kill Switch"
    override val killSwitchDesc: String = "Block all traffic if VPN disconnects unexpectedly"
    override val smartSplit: String = "Smart Split"
    override val smartSplitDesc: String = "Route Iranian sites directly with TLS fragmentation"
    override val exitIpLabel: String = "EXIT IP"
    override val countryLabel: String = "COUNTRY"
    override val bootstrapLabel: String = "BOOTSTRAP"
    override val speedLabel: String = "SPEED"
    override val connectWarp: String = "Connect via WARP"
    override val connectMasque: String = "Connect via MASQUE"
    override val connectCustom: String = "Connect Custom Proxy"
    override val useBestCleanIpWarp: String = "FASTEST CLEAN IP → WARP"
    override val zeroTrustTitle: String = "Cloudflare Zero Trust"
    override val zeroTrustDesc: String = "Authenticate with your organization team domain for dedicated, unblocked access"
    override val zeroTrustTeam: String = "Team Domain"
    override val zeroTrustEmail: String = "Email Address"
    override val zeroTrustCode: String = "6-Digit Code"
    override val zeroTrustSendCode: String = "Send Code"
    override val zeroTrustVerify: String = "Verify & Enroll"
    override val zeroTrustServiceToken: String = "Service Token"
    override val zeroTrustClientId: String = "Client ID"
    override val zeroTrustClientSecret: String = "Client Secret"
    override val zeroTrustGateway: String = "Gateway Routing"
    override val zeroTrustGatewayDesc: String = "Route DNS & HTTP through Cloudflare Gateway security policies"
    override val zeroTrustEnrolled: String = "ENROLLED"
    override val zeroTrustClear: String = "Disconnect Team"
    override val zeroTrustOtpMode: String = "Email Code (OTP)"
    override val upstreamProxy: String = "Upstream Proxy"
    override val upstreamProxyDesc: String = "Route outer tunnel through an upstream SOCKS5 or HTTP proxy"
    override val upstreamProxyPlaceholder: String = "e.g. socks5://127.0.0.1:10808 or http://127.0.0.1:8080"
    override val wiwOuter: String = "Outer Hop (IP:Port)"
    override val wiwInner: String = "Inner Hop (IP:Port)"
    override val wiwDesc: String = "Manually pin outer and inner endpoints for WARP-on-WARP double tunnel"
    override val pinAsWarpEndpoint: String = "Pin as WARP Endpoint"
    override val endpointPinnedSuccess: String = "Clean IP pinned as manual endpoint!"
    override val cipherSuites: String = "Cipher Suites"
    override val cipherSuitesPlaceholder: String = "e.g. TLS_AES_128_GCM_SHA256:TLS_CHACHA20_POLY1305_SHA256"
    override val dialMode: String = "Dial Mode"
    override val dialModeAuto: String = "Auto (Dual-Stack)"
    override val dialModeIpv4: String = "IPv4 Only"
    override val dialModeIpv6: String = "IPv6 Only"
    override val unsafeFingerprint: String = "Unsafe (Custom TLS)"
}


internal object FA : AppStrings {
    override val homeSubtitle: String = "اسکنر آی‌پی کلودفلر"
    override val quickScan: String = "اسکن سریع"
    override val quickScanDesc: String = "اسکن آی‌پی‌های تصادفی کلودفلر"
    override val customScan: String = "اسکن سفارشی"
    override val customScanDesc: String = "تنظیم جزئیات"
    override val testIps: String = "تست آی‌پی"
    override val testIpsDesc: String = "بررسی یک لیست"
    override val discoverColos: String = "کشف دیتاسنترها"
    override val discoverColosDesc: String = "یافتن دیتاسنترهای در دسترس"
    override val telegramChannel: String = "کانال تلگرام"
    override val join: String = "عضویت →"
    override val cfRanges: String = "رنج‌های کلودفلر:"
    override val cidrs: String = "بلوک"
    override val license: String = "مجوز MIT"
    override val count: String = "تعداد"
    override val workers: String = "نخ‌ها"
    override val timeout: String = "مهلت زمانی"
    override val port: String = "پورت"
    override val advancedOptions: String = "تنظیمات پیشرفته"
    override val mode: String = "حالت"
    override val speedTest: String = "تست سرعت"
    override val openSiteFallback: String = "آی‌پی از سایت‌های باز"
    override val openSiteFallbackHint: String = "اگر هیچ آی‌پی از رنج جواب نداد، سایت‌های باز کلودفلر را resolve کرده و آی‌پی‌هایشان بررسی می‌شود"
    override val configOptional: String = "کانفیگ (اختیاری)"
    override val configPlaceholder: String = "vless:// یا trojan:// برای کانفیگ آماده"
    override val configParsed: String = "شناسایی شد · تست سرعت از طریق این کانفیگ انجام می‌شود"
    override val configUnrecognized: String = "✕ لینک ناشناخته · vless:// یا trojan:// انتظار می‌رفت"
    override val startScan: String = "شروع اسکن"
    override val measuringSpeed: String = "اندازه‌گیری سرعت"
    override val stabilityCheck: String = "بررسی پایداری"
    override val resolvingOpenSites: String = "یافتن سایت‌های باز"
    override val probing: String = "در حال بررسی"
    override val cancel: String = "لغو"
    override val fallbackBanner: String = "هیچ آی‌پی از رنج جواب نداد — آی‌پی سایت‌های باز کلودفلر در حال بررسی است"
    override val healthy: String = "سالم"
    override val probed: String = "بررسی‌شده"
    override val done: String = "انجام‌شده"
    override val tested: String = "تست‌شده"
    override val elapsed: String = "زمان"
    override val phaseHandshake: String = "مرحله ۱/۲ · بررسی هندشیک"
    override val phaseThroughConfig: String = "از طریق کانفیگ شما"
    override val phaseEdgesMeasured: String = "آی‌پی اندازه‌گیری‌شده"
    override val phaseDnsLookup: String = "جایگزین · جست‌وجوی DNS سایت‌های باز کلودفلر"
    override val phaseFallbackEdges: String = "جایگزین · بررسی آی‌پی سایت‌های باز کلودفلر"
    override val radar: String = "رادار"
    override val copyIp: String = "کپی آی‌پی"
    override val updateAvailable: String = "نسخه جدید در دسترس است"
    override val updateDesc: String = "نسخه جدید (%s) در گیت‌هاب در دسترس است. آیا می‌خواهید آن را دانلود کنید؟"
    override val downloadNow: String = "دانلود نسخه جدید"
    override val later: String = "بعداً"
    override val localSub: String = "لینک ساب محلی"
    override val localSubDesc: String = "این لینک را در v2rayNG یا NekoBox کپی کنید تا نتایج خودکار همگام شوند."
    override val smartStop: String = "توقف هوشمند"
    override val smartStopDesc: String = "توقف اسکن به محض پیدا شدن تعداد کافی آی‌پی سالم."
    override val fragment: String = "تکه تکه کردن (Fragment)"
    override val fragmentDesc: String = "تکه تکه کردن بسته‌ها برای دور زدن فیلترینگ شدید SNI."
    override val loadIsp: String = "بارگذاری رنج اپراتور"
    override val mci: String = "همراه اول"
    override val irancell: String = "ایرانسل"
    override val mokhaberat: String = "مخابرات"
    override val rightel: String = "رایتل"
    override val gaming: String = "گیمینگ"
    override val streaming: String = "استریم"
    override val stability: String = "پایداری"
    override val gradeS: String = "عالی (پینگ < ۸۰ms، سرعت > ۲۰Mbps)"
    override val gradeA: String = "خیلی خوب (پینگ < ۱۲۰ms، سرعت > ۱۰Mbps)"
    override val gradeB: String = "خوب (پینگ < ۱۸۰ms، سرعت > ۳Mbps)"
    override val gradeC: String = "متوسط (سایر آی‌پی‌های سالم)"
    override val results: String = "نتایج"
    override val healthyIpsTopShown: String = "آی‌پی سالم · نمایش %d مورد برتر"
    override val bestMs: String = "بهترین (ms)"
    override val copyConfigs: String = "کپی %d کانفیگ"
    override val share: String = "اشتراک"
    override val copy: String = "کپی"
    override val scanAgain: String = "اسکن دوباره"
    override val noResults: String = "هیچ آی‌پی سالمی پیدا نشد"
    override val noResultsHint: String = "آی‌پی بیشتر، پورت دیگر یا مهلت بیشتری امتحان کنید"
    override val copiedConfigs: String = "%d کانفیگ آماده کپی شد"
    override val copiedIps: String = "%d آی‌پی کپی شد"
    override val copiedConfigFor: String = "کانفیگ %s کپی شد"
    override val copiedIp: String = "کپی شد: %s"
    override val pasteIps: String = "آی‌پی‌ها را بچسبانید — هر خط یکی یا CSV"
    override val loadFile: String = "بارگذاری .txt / .csv"
    override val fixedSettings: String = "تنظیمات ثابت"
    override val startTest: String = "شروع تست"
    override val ipsSuffix: String = "آی‌پی"
    override val popDiscovery: String = "کشف دیتاسنتر"
    override val popDiscoveryDesc: String = "۳۰۰ آی‌پی تصادفی IPv4 را با HTTP بررسی می‌کند و نتایج را بر اساس دیتاسنتر کلودفلر (colo) " +
        "گروه‌بندی می‌کند. برای دیدن اینکه کدام دیتاسنترها از شبکه شما در دسترس‌اند مفید است."
    override val specs: String = "مشخصات"
    override val startDiscovery: String = "شروع کشف"
    override val about: String = "درباره"
    override val aboutBlurb: String = "یک اسکنر بومی آی‌پی تمیز کلودفلر برای شبکه‌های محدود — آی‌پی‌های سریع و در دسترس کلودفلر " +
        "را برای کانفیگ‌های VLESS / Trojan پیدا می‌کند."
    override val telegram: String = "کانال تلگرام"
    override val gradeLegend: String = "راهنمای رتبه‌بندی کیفیت"
    override val gamingDesc: String = "پینگ پایدار و بدون پکت‌لاس"
    override val streamingDesc: String = "پهنای باند بالا (بیش از ۱۵Mbps)"
    override val stabilityDesc: String = "مناسب برای اتصال طولانی‌مدت"
    override val tierTurbo: String = "توربو"
    override val tierTurboDesc: String = "سریع‌ترین حالت، بررسی TCP"
    override val tierBalanced: String = "متعادل"
    override val tierBalancedDesc: String = "پروفایل بهینه پیش‌فرض"
    override val tierThorough: String = "دقیق"
    override val tierThoroughDesc: String = "بررسی عمیق TLS"
    override val tierStealth: String = "نامرئی"
    override val tierStealthDesc: String = "آهسته و محتاط برای فیلترینگ شدید"
    override val tierIronclad: String = "آهنین"
    override val tierIroncladDesc: String = "بیشترین پایداری، بررسی کامل HTTP"
    override val tierCustom: String = "سفارشی"
    override val tierCustomDesc: String = "تنظیمات دستی"
    // VPN Mode
    override val vpnMode: String = "اتصال وی‌پی‌ان"
    override val vpnModeDesc: String = "اتصال مستقیم و تانل پروکسی"
    override val connect: String = "اتصال"
    override val disconnect: String = "قطع اتصال"
    override val connecting: String = "در حال اتصال..."
    override val connected: String = "متصل شد"
    override val disconnected: String = "قطع شده"
    override val activeConfig: String = "پروفایل فعال"
    override val noActiveConfig: String = "هیچ کانفیگی انتخاب نشده"
    override val tapToSelect: String = "برای انتخاب یا وارد کردن کانفیگ کلیک کنید"
    override val testDelay: String = "تست پینگ"
    override val testingDelay: String = "..."
    override val delayMs: String = "میلی‌ثانیه"
    override val cleanIpActive: String = "آی‌پی تمیز فعال است"
    override val directHost: String = "هاست اصلی سرور"
    override val cleanIpOption: String = "اتصال با آی‌پی تمیز"
    override val cleanIpOptionDesc: String = "هدایت این کانفیگ از طریق لبه سریع کلودفلر"
    override val manageConfigs: String = "مدیریت کانفیگ‌ها"
    override val importConfig: String = "افزودن کانفیگ"
    override val pasteConfigPrompt: String = "لینک vless://، trojan:// یا stormdns:// را وارد کنید"
    override val saveConfig: String = "ذخیره کانفیگ"
    override val deleteConfig: String = "حذف"
    override val select: String = "انتخاب"
    override val connectWithCleanIp: String = "اتصال با این آی‌پی تمیز"
    override val vpnPermissionRequired: String = "برای برقراری تانل، مجوز وی‌پی‌ان لازم است"
    override val connectedDuration: String = "مدت اتصال"
    override val vpnBypassIr: String = "دور زدن سایت‌های ایرانی"
    override val vpnBypassIrDesc: String = "سرویس‌های داخلی مثل بانک‌ها و سایت‌های .ir مستقیم و بدون تانل وصل می‌شوند"
    override val dnsTool: String = "تنظیم‌کننده DNS"
    override val dnsToolDesc: String = "یافتن و اعمال سریع‌ترین DNS"
    override val dnsInfo: String = "رزالورهای DNS را روی شبکه‌ی خودت اندازه بگیر و سریع‌ترین را به تانل VPN اعمال کن. DNSهای تحریم‌شکن سایت‌های تحریمی را هم برای ترافیک مستقیم باز می‌کنند. با اتصال بعدی اعمال می‌شود."
    override val dnsTestAll: String = "تست همه رزالورها"
    override val dnsTesting: String = "در حال تست"
    override val dnsFastest: String = "سریع‌ترین"
    override val dnsUseFastest: String = "استفاده از سریع‌ترین"
    override val dnsApplied: String = "DNS ثبت شد. برای اعمال، VPN را دوباره وصل کنید."
    override val dnsNoAnswer: String = "بدون پاسخ"
    override val dnsStart: String = "شروع"
    override val dnsStop: String = "توقف"
    override val dnsResume: String = "ادامه"
    override val dnsWorkers: String = "ورکرها"
    override val dnsTotal: String = "کل"
    override val dnsValid: String = "معتبر"
    override val dnsRejected: String = "رد شده"
    override val dnsProgress: String = "پیشرفت"
    override val dnsCatAll: String = "همه"
    override val dnsCatAntiSanction: String = "تحریم‌شکن"
    override val dnsCatGlobal: String = "بین‌المللی"
    override val dnsCatCustom: String = "سفارشی"
    override val dnsAddCustom: String = "افزودن DNS سفارشی"
    override val dnsCustomName: String = "نام رزالور"
    override val dnsCustomIp: String = "آدرس آی‌پی"
    override val dnsSave: String = "ذخیره رزالور"
    override val dnsImport: String = "ایمپورت لیست"
    override val dnsExport: String = "کپی معتبرها"
    override val dnsImportPrompt: String = "خطوط را به صورت Name|IP یا آی‌پی تکی بچسبانید"
    override val dnsCopiedValid: String = "رزالورهای پاسخ‌دهنده در کلیپ‌بورد کپی شدند"
    override val dnsDelete: String = "حذف"
    override val dnsTargetDomain: String = "دامنه تست"
    override val dnsWarningHighWorkers: String = "تعداد زیاد ورکر ممکن است جیتر شبکه را افزایش دهد."
    override val dnsSearchHint: String = "جستجوی نام یا آی‌پی..."
    override val stormTune: String = "تنظیم خودکار StormDNS"
    override val stormTuneDesc: String = "پروفایل‌های MTU اندازه‌گیری‌شده را روی اپراتور تو تست می‌کند و سریع‌ترین را برای تونل DNS نگه می‌دارد. به یک پروفایل StormDNS فعال نیاز دارد و باید VPN خاموش باشد."
    override val stormTuneRun: String = "اجرای تنظیم خودکار"
    override val stormTuneStop: String = "توقف"
    override val stormTuneAggressive: String = "شامل حالت تهاجمی"
    override val stormTuneApplied: String = "اعمال شد. برای تغییر، تونل DNS را دوباره وصل کنید."
    override val stormTuneNeedProfile: String = "اول یک پروفایل StormDNS / CottenDNS انتخاب کنید"
    override val stormTuneNeedsIdle: String = "قبل از اجرای تنظیم خودکار، VPN را قطع کنید"
    override val stormTuneActive: String = "پروفایل فعال"
    // Asha Guard Anti-Censorship Transports & Modes
    override val transportWireguard: String = "وایرگارد"
    override val transportWireguardDesc: String = "پروتکل پرسرعت مستقیم UDP بر پایه Noise"
    override val transportMasque: String = "ماسک (MASQUE)"
    override val transportMasqueDesc: String = "دور زدن اختلال با CONNECT-IP روی HTTP/3 و HTTP/2"
    override val transportGool: String = "وارپ دوگانه"
    override val transportGoolDesc: String = "دو لایه تانل وایرگارد تو در تو برای فیلترینگ شدید"
    override val transportPsiphon: String = "سایفون"
    override val transportPsiphonDesc: String = "سیستم نردبانی چند پروتکلی با ۲۵ کشور خروجی"
    override val transportTor: String = "تور (Tor)"
    override val transportTorDesc: String = "مسیریابی پیازی امن با پل‌های Lyrebird"
    override val transportShard: String = "شارد (SHARD)"
    override val transportShardDesc: String = "استخر لبه کلودفلر همراه با فرگمنت TLS"
    override val transportCustom: String = "پروکسی سفارشی"
    override val transportCustomDesc: String = "کانفیگ‌های VLESS / تروجان / StormDNS با آی‌پی تمیز"
    override val psiphonOverWarp: String = "سایفون از روی وارپ"
    override val psiphonOverWarpDesc: String = "هدایت تانل سایفون از درون کلودفلر وارپ جهت عبور از مسدودی اپراتور"
    override val torOverWarp: String = "تور از روی وارپ"
    override val torOverWarpDesc: String = "هدایت اتصال اولیه تور از درون وارپ جهت اتصال قطعی"
    override val exitCountry: String = "کشور خروجی"
    override val autoCountry: String = "خودکار (سریع‌ترین)"
    override val torBridgeMode: String = "نوع پل ارتباطی تور"
    override val torBridgeAuto: String = "خودکار (مستقیم → Meek → obfs4 → Snowflake)"
    override val torBridgeDirect: String = "مستقیم (بدون پل)"
    override val torBridgeMeek: String = "پل Meek (پشت CDN)"
    override val torBridgeObfs4: String = "پل obfs4 (تغییر الگو)"
    override val torBridgeSnowflake: String = "پل Snowflake (وب‌ارتی‌سی)"
    override val outerTransport: String = "لایه بیرونی وارپ"
    override val outerTransportDesc: String = "پروتکل لایه زیرین تانل‌های ترکیبی"
    override val outerAuto: String = "خودکار (ماسک → وایرگارد → وارپ دوگانه)"
    override val tunnelType: String = "نوع تانل"
    override val tunnelTypeVpn: String = "وی‌پی‌ان کل دستگاه"
    override val tunnelTypeVpnDesc: String = "هدایت تمام برنامه‌های گوشی از داخل تانل"
    override val tunnelTypeProxy: String = "پروکسی محلی SOCKS5 / HTTP"
    override val tunnelTypeProxyDesc: String = "بدون نیاز به مجوز وی‌پی‌ان. مناسب تلگرام، مرورگر یا اشتراک با سایر دستگاه‌ها."
    override val lanSharing: String = "اشتراک‌گذاری روی شبکه محلی و هات‌اسپات"
    override val lanSharingDesc: String = "گوش دادن روی 0.0.0.0 تا لپ‌تاپ، تلویزیون یا سایر دستگاه‌ها متصل شوند"
    override val proxyPort: String = "پورت SOCKS5"
    override val httpProxyPort: String = "پورت HTTP"
    override val localIpAddress: String = "آدرس محلی اتصال"
    override val killSwitch: String = "کیل سوییچ (Kill Switch)"
    override val killSwitchDesc: String = "قطع کامل اینترنت در صورت قطع ناگهانی وی‌پی‌ان برای حفظ امنیت"
    override val smartSplit: String = "اسپلیت هوشمند"
    override val smartSplitDesc: String = "عبور مستقیم سایت‌های داخلی با فرگمنت هوشمند TLS برای سرعت بالا"
    override val exitIpLabel: String = "آی‌پی خروجی"
    override val countryLabel: String = "کشور"
    override val bootstrapLabel: String = "پیشرفت اتصال"
    override val speedLabel: String = "سرعت"
    override val connectWarp: String = "اتصال با وارپ (WireGuard)"
    override val connectMasque: String = "اتصال با مسک (MASQUE)"
    override val connectCustom: String = "اتصال با پروکسی سفارشی"
    override val useBestCleanIpWarp: String = "سریع‌ترین آی‌پی تمیز → وارپ"
    override val zeroTrustTitle: String = "کلودفلر زیروتراست (سازمانی)"
    override val zeroTrustDesc: String = "اتصال به شبکه اختصاصی سازمان شما در کلودفلر با احراز هویت مستقیم"
    override val zeroTrustTeam: String = "دامنه تیم (Team Domain)"
    override val zeroTrustEmail: String = "آدرس ایمیل"
    override val zeroTrustCode: String = "کد ۶ رقمی ایمیل"
    override val zeroTrustSendCode: String = "ارسال کد"
    override val zeroTrustVerify: String = "تایید و ثبت‌نام"
    override val zeroTrustServiceToken: String = "توکن سرویس (Service Token)"
    override val zeroTrustClientId: String = "شناسه کلاینت (Client ID)"
    override val zeroTrustClientSecret: String = "رمز کلاینت (Client Secret)"
    override val zeroTrustGateway: String = "مسیریابی از گیت‌وی (Gateway)"
    override val zeroTrustGatewayDesc: String = "هدایت ترافیک DNS و وب از سیاست‌های امنیتی گیت‌وی کلودفلر"
    override val zeroTrustEnrolled: String = "متصل به سازمان"
    override val zeroTrustClear: String = "خروج از حساب سازمانی"
    override val zeroTrustOtpMode: String = "کد یکبارمصرف ایمیل (OTP)"
    override val upstreamProxy: String = "پروکسی بالادست (Upstream Proxy)"
    override val upstreamProxyDesc: String = "هدایت تانل بیرونی از داخل یک پروکسی محلی یا ریموت SOCKS5 / HTTP"
    override val upstreamProxyPlaceholder: String = "مثال: socks5://127.0.0.1:10808 یا http://127.0.0.1:8080"
    override val wiwOuter: String = "هاپ بیرونی (IP:Port)"
    override val wiwInner: String = "هاپ درونی (IP:Port)"
    override val wiwDesc: String = "تنظیم دستی هاپ‌های بیرونی و درونی برای تانل دوگانه WARP-on-WARP"
    override val pinAsWarpEndpoint: String = "تنظیم به عنوان اندپوینت وارپ"
    override val endpointPinnedSuccess: String = "آی‌پی تمیز به عنوان نقطه اتصال دستی ست شد!"
    override val cipherSuites: String = "مجموعه رمزنگاری (Cipher Suites)"
    override val cipherSuitesPlaceholder: String = "مثال: TLS_AES_128_GCM_SHA256:..."
    override val dialMode: String = "حالت شماره‌گیری شبکه (Dial Mode)"
    override val dialModeAuto: String = "خودکار (دوگانه)"
    override val dialModeIpv4: String = "فقط IPv4"
    override val dialModeIpv6: String = "فقط IPv6"
    override val unsafeFingerprint: String = "نامطمئن (سفارشی بدون شبیه‌سازی)"
}


fun stringsFor(lang: Lang): AppStrings = if (lang == Lang.FA) FA else EN

val LocalStrings = staticCompositionLocalOf<AppStrings> { EN }
val LocalLang = staticCompositionLocalOf { Lang.EN }

/** Convert ASCII digits to Persian digits for display when in Persian mode. */
fun String.localizeDigits(lang: Lang): String {
    if (lang != Lang.FA) return this
    val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return buildString(length) {
        for (c in this@localizeDigits) append(if (c in '0'..'9') fa[c - '0'] else c)
    }
}
