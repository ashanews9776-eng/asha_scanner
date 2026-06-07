# Asha Scanner · اسکنر آشا

An Android port of [SenPaiScanner](https://github.com/MatinSenPai/SenPaiScanner) — a
**clean Cloudflare IP scanner** for VLESS / Trojan proxies, rebuilt natively in Kotlin +
Jetpack Compose, **Persian-first (RTL) with an English toggle**, designed for the Iranian
community on unreliable networks.

You paste a `vless://` or `trojan://` link, the app finds the fastest, cleanest Cloudflare
edge IPs, and you copy the winners into your existing client (v2rayNG, NekoBox, …).

## How it works (faithful to the original two phases)

**Phase 1 — Probe.** Generates random IPs from Cloudflare's published CIDR ranges (bundled
offline) and tests each with an escalating handshake — `TCP → TLS (with SNI rotation to
dodge DPI) → HTTPS GET /cdn-cgi/trace` — splitting the timeout budget across dial / TLS /
request and measuring latency, jitter and packet loss. Mirrors `internal/prober`.

**Phase 2 — Validate.** Ranks the survivors by latency and measures the **real download
throughput** of the best ones, then re-ranks by speed. Two strategies, chosen automatically:

- **Tunnel-through-config** (when you paste a `vless://`/`trojan://` link, `TunnelValidator`):
  a pure-Kotlin VLESS/Trojan client dials each candidate edge, completes the outer TLS
  handshake with your config's SNI over a plain-TCP or WebSocket transport, speaks the real
  proxy handshake to your origin, and pulls `speed.cloudflare.com/__down` *through the actual
  tunnel*. So latency + speed reflect the genuine end-to-end path a v2rayNG/NekoBox client
  would get on that IP. Transports it can't speak natively (Reality, gRPC, xhttp) fall back
  gracefully.
- **Direct edge** (no config, e.g. Test-IPs mode, `DirectThroughputValidator`): pulls the
  same speed endpoint straight through each candidate IP — the technique CloudflareSpeedTest
  uses.

Both are pure-Kotlin with no native dependencies. The byte-exact proxy framing lives in
`ProxyHandshake` (unit-tested against known vectors).

## Architecture

```
core/
  model/      Models.kt            data classes, enums (ProbeMode, Protocol, ScanResult…)
  ipsrc/      CloudflareRanges.kt  official CF v4/v6 CIDRs (fallback) + IPv6 source
              IpSource.kt          random IP generation (port of internal/ipsrc)
              (assets/cf_ipv4.txt) precise ~4.6k curated /24 ranges (ircfspace list),
                                   loaded at startup as the primary IPv4 source
  parser/     ProxyParser.kt       vless:// & trojan:// parser (port of xraytest/parser.go)
  net/        Tls.kt               dial a specific IP with chosen SNI/ALPN
  prober/     Prober.kt            Phase-1 TCP/TLS/HTTP probing (port of internal/prober)
  validator/  Validator.kt         strategy interface
              TunnelValidator.kt             pure-Kotlin VLESS/Trojan tunnel Phase-2
              ProxyHandshake.kt              byte-exact VLESS/Trojan/WS framing (tested)
              DirectThroughputValidator.kt   direct-edge Phase-2 (no config)
              XrayConfigBuilder.kt           full port of xraytest/builder.go
              XrayValidator.kt               drop-in slot for xray-core
  engine/     ScanEngine.kt        orchestration -> Flow<ScanProgress>
              ResultSort.kt        ranking (port of result.go comparators)
  output/     Exporter.kt          clipboard / share formatting
ui/           Compose screens, theme (Vazirmatn), FA/EN i18n, ViewModel
```

## Optional: full xray-core Phase 2 (true end-to-end speed)

`TunnelValidator` already measures speed *through* the proxy for VLESS/Trojan over TCP/WS.
If you want byte-for-byte parity with the desktop tool for **every** transport (including
Reality / gRPC / xhttp), the xray-core integration point is already built:

1. Add an xray-core mobile AAR (e.g. AndroidLibXrayLite / libv2ray) to `app/libs`.
2. Implement the start/stop glue in `XrayValidator` — `XrayConfigBuilder` already emits the
   exact JSON xray-core expects, with the candidate IP swapped into the outbound.
3. Construct `ScanEngine(XrayValidator())`. Nothing else changes.

## Build

```
./gradlew :app:assembleDebug      # APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # core logic tests
```

Min SDK 24 · Target SDK 36 · Kotlin 2.2 · Compose. Vazirmatn is bundled for offline
Persian/Latin rendering.
