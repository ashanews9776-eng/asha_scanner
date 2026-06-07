package com.ahoura.asha_scanner_ip.core.validator

import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult

/**
 * Drop-in slot for full end-to-end Phase-2 validation through a real xray-core
 * tunnel (true 1:1 with the SenPaiScanner original).
 *
 * INTEGRATION (no changes needed elsewhere — the engine accepts any [Validator]):
 *  1. Add the xray-core mobile AAR (e.g. AndroidLibXrayLite / libv2ray) to
 *     `app/libs` and depend on it in `app/build.gradle.kts`.
 *  2. In [validate]:
 *       val socksPort = freeLocalPort()
 *       val json = XrayConfigBuilder.buildJson(proxy!!, result.ip, socksPort)
 *       start the xray instance with `json` (write to a temp file / pass bytes),
 *       wait until 127.0.0.1:socksPort accepts connections.
 *  3. Run the speed + latency measurement *through* that SOCKS5 proxy
 *     (java.net.Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
 *     fetching https://cp.cloudflare.com/cdn-cgi/trace and a speed endpoint;
 *     require the response to contain "colo=" for a pass.
 *  4. Stop the xray instance in `finally` and return result.copy(
 *       throughputBytesPerSec = measured, speedTested = true, healthy = pass).
 *
 * The [XrayConfigBuilder] already produces the exact config xray-core expects,
 * so only the native start/stop glue remains. Until the AAR is bundled the app
 * uses [DirectThroughputValidator], which needs no native code.
 */
class XrayValidator : Validator {
    override suspend fun validate(
        result: ScanResult,
        proxy: ProxyConfig?,
        cfg: ScanConfig,
    ): ScanResult {
        throw NotImplementedError(
            "Bundle an xray-core AAR and implement the start/stop glue described " +
                "in XrayValidator. Use DirectThroughputValidator until then."
        )
    }
}
