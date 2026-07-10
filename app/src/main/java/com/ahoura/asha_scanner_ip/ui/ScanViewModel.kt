package com.ahoura.asha_scanner_ip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahoura.asha_scanner_ip.core.engine.ScanEngine
import com.ahoura.asha_scanner_ip.core.engine.SortKey
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanPhase
import com.ahoura.asha_scanner_ip.core.model.ScanProgress
import com.ahoura.asha_scanner_ip.core.net.SubServer
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import com.ahoura.asha_scanner_ip.data.SettingsStore
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateInfo(
    val version: String,
    val url: String,
    val changelog: String? = null,
)

data class UiState(
    val configText: String = "",
    val parsedProxy: ProxyConfig? = null,
    val parseError: Boolean = false,
    val scanConfig: ScanConfig = ScanConfig(),
    val progress: ScanProgress = ScanProgress(),
    val isScanning: Boolean = false,
    val sortKey: SortKey = SortKey.SPEED,
    val customRangesText: String = "",
    val testIpsText: String = "",
    val fallbackDomainsText: String = "",
    val updateInfo: UpdateInfo? = null,
    val subUrl: String? = null,
    val ispInfo: String = "Detecting ISP...",
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ScanEngine()
    private val settings = SettingsStore(app)
    private val subServer = SubServer()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val language: StateFlow<Lang> = settings.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, Lang.EN)

    private var scanJob: Job? = null

    // Built-in open-site fallback domains from assets/cf_domains.txt. Kept apart
    // from the user's custom additions so the two can be merged on every edit.
    private var bundledDomains: List<String> = emptyList()

    init {
        // Load the precise Cloudflare IPv4 ranges (ircfspace list) and the
        // open-site fallback domain list bundled in assets, off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val ranges = readAssetLines("cf_ipv4.txt")
            val domains = readAssetLines("cf_domains.txt")
            bundledDomains = domains
            _state.update {
                it.copy(
                    scanConfig = it.scanConfig.copy(
                        customV4Ranges = if (ranges.isNotEmpty()) ranges else it.scanConfig.customV4Ranges,
                        fallbackDomains = mergeDomains(domains, it.fallbackDomainsText),
                    )
                )
            }
            checkUpdate()
            detectIpInfo()
        }
    }

    private fun detectIpInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(ispInfo = "Analyzing Network...") }
            
            // 1. Get Cloudflare Trace info (most reliable way)
            val cfInfo = fetchCloudflareTrace()
            
            // 2. Try to get a more descriptive ISP name from APIs if possible
            val apiIsp = if (cfInfo == null || cfInfo.isp.isEmpty()) fetchIspFromMultipleApis() else null
            
            val finalIsp = apiIsp ?: cfInfo?.isp ?: "Unknown Provider"
            val finalLoc = cfInfo?.loc ?: "??"
            val finalColo = cfInfo?.colo ?: "???"
            
            val finalInfo = if (cfInfo != null || apiIsp != null) {
                "$finalIsp ($finalLoc · $finalColo)"
            } else {
                "Offline / Unknown"
            }
            
            _state.update { it.copy(ispInfo = finalInfo) }
        }
    }

    private fun fetchCloudflareTrace(): TraceBasic? {
        val endpoints = listOf(
            "https://1.1.1.1/cdn-cgi/trace",
            "http://1.1.1.1/cdn-cgi/trace"
        )
        
        for (urlStr in endpoints) {
            try {
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                if (conn.responseCode == 200) {
                    val lines = conn.inputStream.bufferedReader().readLines()
                    val colo = lines.find { it.startsWith("colo=") }?.split("=")?.getOrNull(1) ?: "???"
                    val loc = lines.find { it.startsWith("loc=") }?.split("=")?.getOrNull(1) ?: "??"
                    val asLine = lines.find { it.startsWith("as=") }?.split("=")?.getOrNull(1) ?: ""
                    
                    // Comprehensive mapping for Iranian ISPs
                    val isp = when {
                        asLine.contains("196822") || asLine.contains("43754") -> "MCI"
                        asLine.contains("44244") || asLine.contains("197285") || asLine.contains("201193") -> "Irancell"
                        asLine.contains("31549") -> "Rightel"
                        asLine.contains("56433") -> "Shatel"
                        asLine.contains("12880") || asLine.contains("51070") -> "Mokhaberat"
                        asLine.contains("16322") -> "ParsOnline"
                        asLine.contains("44376") -> "Asiatech"
                        asLine.contains("206065") -> "Zitel"
                        asLine.contains("50810") -> "Mobinnet"
                        asLine.isNotEmpty() -> asLine.removePrefix("AS").split(" ").getOrNull(0) ?: ""
                        else -> ""
                    }
                    return TraceBasic(colo, loc, isp)
                }
            } catch (e: Exception) { continue }
        }
        return null
    }

    private fun fetchIspFromMultipleApis(): String? {
        val apis = listOf(
            "http://ip-api.com/json/?fields=isp",
            "https://api.db-ip.com/v2/free/self/organization"
        )
        for (apiUrl in apis) {
            try {
                val url = java.net.URL(apiUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2500
                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().readText().trim()
                    val found = "\"([^\"]+)\"".toRegex().findAll(res).lastOrNull()?.groupValues?.get(1)
                    if (found != null && found.length < 40 && !found.contains("{")) return found
                }
            } catch (e: Exception) { continue }
        }
        return null
    }

    private data class TraceBasic(val colo: String, val loc: String, val isp: String)

    private fun checkUpdate() {
        try {
            val url = java.net.URL("https://api.github.com/repos/ashanews9776-eng/asha_scanner/releases/latest")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                // Crude but effective JSON extraction for tag_name, html_url and body
                val tagName = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(response)?.groupValues?.get(1)
                val htmlUrl = "\"html_url\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(response)?.groupValues?.get(1)
                val body = "\"body\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(response)?.groupValues?.get(1)
                    ?.replace("\\r\\n", "\n")?.replace("\\n", "\n")

                val app = getApplication<Application>()
                val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
                val currentVersion = "v${packageInfo.versionName}"

                if (tagName != null && htmlUrl != null && tagName != currentVersion) {
                    _state.update { it.copy(updateInfo = UpdateInfo(tagName, htmlUrl, body)) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(updateInfo = null) }
    }

    private fun readAssetLines(name: String): List<String> = runCatching {
        getApplication<Application>().assets.open(name).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
    }.getOrDefault(emptyList())

    private fun parseDomains(text: String): List<String> =
        text.split('\n', ',', ' ', '\t', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun mergeDomains(bundled: List<String>, userText: String): List<String> =
        (bundled + parseDomains(userText)).distinct()

    fun onConfigChange(text: String) {
        val trimmed = text.trim()
        val parsed = if (trimmed.isEmpty()) null else runCatching { ProxyParser.parse(trimmed) }.getOrNull()
        _state.update {
            it.copy(
                configText = text,
                parsedProxy = parsed,
                parseError = trimmed.isNotEmpty() && parsed == null,
                scanConfig = if (parsed != null) it.scanConfig.copy(
                    ports = listOf(parsed.port),
                    sniOverride = parsed.effectiveSni()
                ) else it.scanConfig
            )
        }
    }

    fun updateScanConfig(transform: (ScanConfig) -> ScanConfig) {
        _state.update { it.copy(scanConfig = transform(it.scanConfig)) }
    }

    fun setSortKey(key: SortKey) {
        _state.update { it.copy(sortKey = key) }
    }

    fun onCustomRangesChange(text: String) {
        val cidrs = text.split(',', '\n', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        _state.update {
            it.copy(customRangesText = text, scanConfig = it.scanConfig.copy(extraCidrs = cidrs))
        }
    }

    /** Append the user's own Cloudflare-fronted domains to the fallback list. */
    fun onFallbackDomainsChange(text: String) {
        _state.update {
            it.copy(
                fallbackDomainsText = text,
                scanConfig = it.scanConfig.copy(fallbackDomains = mergeDomains(bundledDomains, text)),
            )
        }
    }

    fun setLanguage(lang: Lang) {
        viewModelScope.launch { settings.setLanguage(lang) }
    }

    fun toggleLanguage() {
        setLanguage(if (language.value == Lang.FA) Lang.EN else Lang.FA)
    }

    /** Parse a paste/CSV blob of IPs into the explicit-IP scan list (Test mode). */
    fun onTestIpsChange(text: String) {
        val ips = text.split('\n', ',', ' ', '\t', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        _state.update {
            it.copy(testIpsText = text, scanConfig = it.scanConfig.copy(explicitIps = ips))
        }
    }

    fun start() {
        val s = _state.value
        val proxy = s.parsedProxy   // may be null — modes that scan random/explicit IPs
        if (s.isScanning) return
        _state.update { it.copy(isScanning = true, progress = ScanProgress(phase = ScanPhase.PROBING)) }
        
        // Start sub server
        viewModelScope.launch { subServer.start() }

        scanJob = viewModelScope.launch {
            try {
                engine.scan(proxy, s.scanConfig).collect { p ->
                    _state.update { it.copy(progress = p, subUrl = subServer.getUrl()) }
                    subServer.updateResults(p.best, proxy)
                }
            } catch (_: Throwable) {
                // cancellation or unexpected error — handled by stop()/final state
            } finally {
                _state.update { st ->
                    if (st.progress.phase == ScanPhase.PROBING || st.progress.phase == ScanPhase.VALIDATING)
                        st.copy(isScanning = false, progress = st.progress.copy(phase = ScanPhase.DONE))
                    else st.copy(isScanning = false)
                }
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        subServer.stop()
        _state.update {
            it.copy(
                isScanning = false,
                subUrl = null,
                progress = it.progress.copy(phase = ScanPhase.CANCELLED),
            )
        }
    }

    override fun onCleared() {
        subServer.stop()
        super.onCleared()
    }

    fun reset() {
        stop()
        _state.update {
            it.copy(
                progress = ScanProgress(),
                testIpsText = "",
                scanConfig = it.scanConfig.copy(explicitIps = emptyList()),
            )
        }
    }

    /** Configure defaults for the Discover-Colos preset. */
    fun prepareDiscover() = updateScanConfig {
        it.copy(
            explicitIps = emptyList(), count = 300, tries = 2, timeoutMs = 5_000,
            mode = com.ahoura.asha_scanner_ip.core.model.ProbeMode.HTTP,
            speedTest = false, top = 50, ports = listOf(443),
        )
    }
}
