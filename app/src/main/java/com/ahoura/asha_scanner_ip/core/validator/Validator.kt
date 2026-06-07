package com.ahoura.asha_scanner_ip.core.validator

import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult

/**
 * Phase-2 validation strategy. Given a Phase-1 survivor, produce an enriched
 * [ScanResult] with measured throughput.
 *
 * Two implementations are intended:
 *  - [DirectThroughputValidator] (default, pure-Kotlin): measures the candidate
 *    Cloudflare edge's real download speed via Cloudflare's own speed endpoint —
 *    the same technique CloudflareSpeedTest uses. No native dependencies.
 *  - An xray-core backed validator (drop-in): routes traffic through the actual
 *    VLESS/Trojan tunnel using the candidate IP, for true end-to-end speed,
 *    exactly like the SenPaiScanner original. See XrayValidator for the contract.
 *
 * The engine treats validators interchangeably, so swapping in xray-core later
 * requires no changes to the scan pipeline.
 */
interface Validator {
    suspend fun validate(result: ScanResult, proxy: ProxyConfig?, cfg: ScanConfig): ScanResult
}
