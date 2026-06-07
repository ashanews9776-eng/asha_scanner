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
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ScanEngine()
    private val settings = SettingsStore(app)

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
        }
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
        scanJob = viewModelScope.launch {
            try {
                engine.scan(proxy, s.scanConfig).collect { p ->
                    _state.update { it.copy(progress = p) }
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
        _state.update {
            it.copy(
                isScanning = false,
                progress = it.progress.copy(phase = ScanPhase.CANCELLED),
            )
        }
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
