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
import com.ahoura.asha_scanner_ip.core.dns.DnsProbe
import com.ahoura.asha_scanner_ip.core.guard.SecureStore
import com.msnguard.vpn.NativeCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val configStore = com.ahoura.asha_scanner_ip.core.config.ConfigStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val language: StateFlow<Lang> = settings.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, Lang.EN)

    val vpnStats: StateFlow<com.ahoura.asha_scanner_ip.core.vpn.VpnStats> = com.ahoura.asha_scanner_ip.core.vpn.VpnManager.stats
    val savedProfiles: StateFlow<List<com.ahoura.asha_scanner_ip.core.vpn.VpnProfile>> = configStore.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeProfile: StateFlow<com.ahoura.asha_scanner_ip.core.vpn.VpnProfile?> = configStore.activeProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val vpnBypassIr: StateFlow<Boolean> = settings.vpnBypassIr
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val vpnDnsIp: StateFlow<String> = settings.vpnDnsIp
        .stateIn(viewModelScope, SharingStarted.Eagerly, "1.1.1.1")

    // ---- Asha Guard Transports & Settings State ----

    private val guardPrefs: android.content.SharedPreferences =
        app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private fun getPrefString(key: String, def: String = ""): String =
        runCatching { guardPrefs.getString(key, def) ?: def }.getOrDefault(def)

    private fun getPrefBool(key: String, def: Boolean = false): Boolean =
        runCatching { guardPrefs.getBoolean(key, def) }.getOrDefault(def)

    private fun getPrefInt(key: String, def: Int = 0): Int =
        runCatching { guardPrefs.getInt(key, def) }.getOrDefault(def)

    val selectedTransport = MutableStateFlow(
        getPrefString("default_protocol", "wireguard")
    )
    val tunnelMode = MutableStateFlow(
        getPrefString("tunnel_mode", "vpn")
    )
    val lanSharing = MutableStateFlow(
        getPrefBool("psiphon_lan_sharing", false)
    )
    val psiphonRegion = MutableStateFlow(
        getPrefString("psiphon_egress_region", "auto")
    )
    val psiphonChained = MutableStateFlow(
        getPrefBool("chain_armed", true)
    )
    val torMode = MutableStateFlow(
        getPrefString("tor_mode", "auto")
    )
    val torRegion = MutableStateFlow(
        getPrefString("tor_exit_region", "auto")
    )
    val torChained = MutableStateFlow(
        getPrefBool("tor_chain_armed", true)
    )
    val chainOuterMode = MutableStateFlow(
        getPrefString("chain_outer_mode", "auto")
    )
    val chainOuterModeTor = MutableStateFlow(
        getPrefString("chain_outer_mode_tor", "auto")
    )
    val killSwitch = MutableStateFlow(
        getPrefBool("kill_switch", false)
    )
    val proxyPort = MutableStateFlow(
        getPrefInt("proxy_listen_port", 10808)
    )
    val masqueTransport = MutableStateFlow(
        getPrefString("default_masque_transport", "h3")
    )

    fun setSelectedTransport(proto: String) {
        selectedTransport.value = proto
        guardPrefs.edit().putString("default_protocol", proto).apply()
    }

    fun setTunnelMode(mode: String) {
        tunnelMode.value = mode
        guardPrefs.edit().putString("tunnel_mode", mode).apply()
    }

    fun setLanSharing(enabled: Boolean) {
        lanSharing.value = enabled
        guardPrefs.edit().putBoolean("psiphon_lan_sharing", enabled).apply()
    }

    fun setPsiphonRegion(region: String) {
        psiphonRegion.value = region
        guardPrefs.edit().putString("psiphon_egress_region", region).apply()
    }

    fun setPsiphonChained(chained: Boolean) {
        psiphonChained.value = chained
        guardPrefs.edit().putBoolean("chain_armed", chained).apply()
    }

    fun setTorMode(mode: String) {
        torMode.value = mode
        guardPrefs.edit().putString("tor_mode", mode).apply()
    }

    fun setTorRegion(region: String) {
        torRegion.value = region
        guardPrefs.edit().putString("tor_exit_region", region).apply()
    }

    fun setTorChained(chained: Boolean) {
        torChained.value = chained
        guardPrefs.edit().putBoolean("tor_chain_armed", chained).apply()
    }

    fun setChainOuterMode(mode: String) {
        chainOuterMode.value = mode
        guardPrefs.edit().putString("chain_outer_mode", mode).apply()
    }

    fun setChainOuterModeTor(mode: String) {
        chainOuterModeTor.value = mode
        guardPrefs.edit().putString("chain_outer_mode_tor", mode).apply()
    }

    fun setKillSwitch(enabled: Boolean) {
        killSwitch.value = enabled
        guardPrefs.edit().putBoolean("kill_switch", enabled).apply()
    }

    fun setProxyPort(port: Int) {
        proxyPort.value = port
        guardPrefs.edit().putInt("proxy_listen_port", port).apply()
    }

    fun setMasqueTransport(t: String) {
        masqueTransport.value = t
        guardPrefs.edit().putString("default_masque_transport", t).apply()
    }

    val upstreamProxy = MutableStateFlow(
        getPrefString("upstream_proxy", "")
    )
    val wiwOuter = MutableStateFlow(
        getPrefString("wiw_outer", "")
    )
    val wiwInner = MutableStateFlow(
        getPrefString("wiw_inner", "")
    )
    val manualEndpoint = MutableStateFlow(
        getPrefString("manual_endpoint", "")
    )

    // Cloudflare Zero Trust states
    val zeroTrustTeam = MutableStateFlow(
        SecureStore.getSecret(getApplication(), "zero_trust_team")
    )
    val zeroTrustEmail = MutableStateFlow(
        SecureStore.getSecret(getApplication(), "zero_trust_email")
    )
    val zeroTrustToken = MutableStateFlow(
        SecureStore.getSecret(getApplication(), "zero_trust_token")
    )
    val zeroTrustClientId = MutableStateFlow(
        SecureStore.getSecret(getApplication(), "zero_trust_client_id")
    )
    val zeroTrustClientSecret = MutableStateFlow(
        SecureStore.getSecret(getApplication(), "zero_trust_client_secret")
    )
    val zeroTrustGateway = MutableStateFlow(
        getPrefBool("zero_trust_gateway", false)
    )
    val zeroTrustOtpSent = MutableStateFlow(false)
    val zeroTrustBusy = MutableStateFlow(false)
    val zeroTrustMessage = MutableStateFlow<String?>(null)

    fun setUpstreamProxy(value: String) {
        upstreamProxy.value = value.trim()
        guardPrefs.edit().putString("upstream_proxy", value.trim()).apply()
    }

    fun setWiwEndpoints(outer: String, inner: String) {
        wiwOuter.value = outer.trim()
        wiwInner.value = inner.trim()
        guardPrefs.edit()
            .putString("wiw_outer", outer.trim())
            .putString("wiw_inner", inner.trim())
            .apply()
    }

    fun setWiwOuter(outer: String) {
        wiwOuter.value = outer.trim()
        guardPrefs.edit().putString("wiw_outer", outer.trim()).apply()
    }

    fun setWiwInner(inner: String) {
        wiwInner.value = inner.trim()
        guardPrefs.edit().putString("wiw_inner", inner.trim()).apply()
    }

    fun setManualEndpoint(endpoint: String) {
        manualEndpoint.value = endpoint.trim()
        guardPrefs.edit().putString("manual_endpoint", endpoint.trim()).apply()
    }

    fun requestZeroTrustCode(team: String, email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            zeroTrustBusy.value = true
            zeroTrustMessage.value = null
            try {
                NativeCore.requestEmailCode(team.trim(), email.trim())
                SecureStore.putSecret(getApplication(), "zero_trust_team", team.trim())
                SecureStore.putSecret(getApplication(), "zero_trust_email", email.trim())
                zeroTrustTeam.value = team.trim()
                zeroTrustEmail.value = email.trim()
                zeroTrustOtpSent.value = true
                zeroTrustMessage.value = "Code sent to ${email.trim()}"
            } catch (e: Exception) {
                zeroTrustMessage.value = "Error: ${e.message ?: "Failed to send code"}"
            } finally {
                zeroTrustBusy.value = false
            }
        }
    }

    fun confirmZeroTrustCode(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            zeroTrustBusy.value = true
            zeroTrustMessage.value = null
            try {
                val token = NativeCore.confirmEmailCode(code.trim())
                SecureStore.putSecret(getApplication(), "zero_trust_token", token)
                zeroTrustToken.value = token
                zeroTrustOtpSent.value = false
                zeroTrustMessage.value = "Zero Trust enrollment successful!"
            } catch (e: Exception) {
                zeroTrustMessage.value = "Verification failed: ${e.message ?: "Invalid code"}"
            } finally {
                zeroTrustBusy.value = false
            }
        }
    }

    fun setZeroTrustServiceToken(team: String, clientId: String, clientSecret: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SecureStore.putSecret(getApplication(), "zero_trust_team", team.trim())
            SecureStore.putSecret(getApplication(), "zero_trust_client_id", clientId.trim())
            SecureStore.putSecret(getApplication(), "zero_trust_client_secret", clientSecret.trim())
            zeroTrustTeam.value = team.trim()
            zeroTrustClientId.value = clientId.trim()
            zeroTrustClientSecret.value = clientSecret.trim()
            zeroTrustMessage.value = "Service token saved!"
        }
    }

    fun setZeroTrustGateway(enabled: Boolean) {
        zeroTrustGateway.value = enabled
        guardPrefs.edit().putBoolean("zero_trust_gateway", enabled).apply()
    }

    fun clearZeroTrust() {
        viewModelScope.launch(Dispatchers.IO) {
            SecureStore.removeSecret(getApplication(), "zero_trust_team")
            SecureStore.removeSecret(getApplication(), "zero_trust_email")
            SecureStore.removeSecret(getApplication(), "zero_trust_token")
            SecureStore.removeSecret(getApplication(), "zero_trust_client_id")
            SecureStore.removeSecret(getApplication(), "zero_trust_client_secret")
            guardPrefs.edit().remove("zero_trust_gateway").apply()
            zeroTrustTeam.value = ""
            zeroTrustEmail.value = ""
            zeroTrustToken.value = ""
            zeroTrustClientId.value = ""
            zeroTrustClientSecret.value = ""
            zeroTrustGateway.value = false
            zeroTrustOtpSent.value = false
            zeroTrustMessage.value = "Zero Trust credentials cleared"
        }
    }

    fun connectWithScannedIpAsWarp(
        context: android.content.Context,
        cleanIp: String,
        port: Int = 443,
        transport: String = "wireguard"
    ) {
        viewModelScope.launch {
            val endpoint = if (cleanIp.contains(":")) cleanIp else "$cleanIp:$port"
            setManualEndpoint(endpoint)
            setSelectedTransport(transport)
            startVpnTransport(context, transport)
        }
    }

    // ---- DNS Tuner state (WhiteDNS-inspired architecture) ----

    enum class DnsScanStatus {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED,
    }

    data class DnsUiState(
        val resolvers: List<DnsProbe.ResolverDef> = emptyList(),
        val results: Map<String, DnsProbe.ProbeResult> = emptyMap(),
        val status: DnsScanStatus = DnsScanStatus.IDLE,
        val workerCount: Int = 8,
        val selectedCategory: DnsProbe.ResolverCategory? = null,
        val targetDomain: String = "digikala.com",
        val done: Int = 0,
        val valid: Int = 0,
        val rejected: Int = 0,
        val total: Int = 0,
        val searchQuery: String = "",
    ) {
        val fraction: Float
            get() = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

        val isRunning: Boolean
            get() = status == DnsScanStatus.RUNNING

        val canResume: Boolean
            get() = !isRunning && (status == DnsScanStatus.PAUSED || status == DnsScanStatus.IDLE) && done > 0 && done < total

        val filteredResolvers: List<DnsProbe.ResolverDef>
            get() {
                val byCategory = if (selectedCategory == null) resolvers else resolvers.filter { it.category == selectedCategory }
                if (searchQuery.isBlank()) return byCategory
                val q = searchQuery.trim().lowercase()
                return byCategory.filter { it.name.lowercase().contains(q) || it.ip.contains(q) }
            }
    }

    private val _dnsState = MutableStateFlow(DnsUiState())
    val dnsState: StateFlow<DnsUiState> = _dnsState.asStateFlow()

    private var scanJob: Job? = null
    private var dnsResolversLoaded = false
    private var dnsTestJob: Job? = null

    /** Load bundled and user-saved custom resolver lists. */
    fun loadDnsResolvers() {
        if (dnsResolversLoaded) return
        dnsResolversLoaded = true
        viewModelScope.launch(Dispatchers.IO) {
            val bundled = readAssetLines("dns_resolvers.txt").mapNotNull { DnsProbe.parseResolverLine(it) }
            val savedCustomText = settings.customDnsResolvers.first()
            val custom = savedCustomText.lines().mapNotNull { DnsProbe.parseResolverLine(it) }.map {
                it.copy(category = DnsProbe.ResolverCategory.CUSTOM)
            }
            val workers = settings.dnsWorkerCount.first()
            val all = (bundled + custom).distinctBy { it.ip }
            _dnsState.update {
                it.copy(
                    resolvers = all,
                    workerCount = workers,
                    total = all.size,
                )
            }
        }
    }

    /** Start or resume parallel resolver probing (lane-capped with workerCount). */
    fun startDnsScan(resume: Boolean = false) {
        val allResolvers = _dnsState.value.resolvers
        if (allResolvers.isEmpty() || _dnsState.value.isRunning) return

        val resolversToProbe = if (resume) {
            allResolvers.filterNot { _dnsState.value.results.containsKey(it.ip) }
        } else {
            allResolvers
        }

        if (resolversToProbe.isEmpty()) {
            _dnsState.update { it.copy(status = DnsScanStatus.COMPLETED) }
            return
        }

        dnsTestJob?.cancel()
        dnsTestJob = viewModelScope.launch {
            if (!resume) {
                _dnsState.update {
                    it.copy(
                        status = DnsScanStatus.RUNNING,
                        results = emptyMap(),
                        done = 0,
                        valid = 0,
                        rejected = 0,
                        total = allResolvers.size,
                    )
                }
            } else {
                _dnsState.update {
                    it.copy(
                        status = DnsScanStatus.RUNNING,
                        total = allResolvers.size,
                    )
                }
            }

            try {
                DnsProbe.probeAll(
                    resolvers = resolversToProbe,
                    domain = _dnsState.value.targetDomain,
                    concurrency = _dnsState.value.workerCount,
                ) { result ->
                    _dnsState.update { curr ->
                        val isValid = result.latencyMs != null
                        curr.copy(
                            results = curr.results + (result.ip to result),
                            done = curr.done + 1,
                            valid = if (isValid) curr.valid + 1 else curr.valid,
                            rejected = if (!isValid) curr.rejected + 1 else curr.rejected,
                        )
                    }
                }
                _dnsState.update { it.copy(status = DnsScanStatus.COMPLETED) }
            } catch (_: CancellationException) {
                _dnsState.update { it.copy(status = DnsScanStatus.PAUSED) }
            }
        }
    }

    /** Gracefully stops an active DNS scan. */
    fun stopDnsScan() {
        dnsTestJob?.cancel()
        _dnsState.update { it.copy(status = DnsScanStatus.PAUSED) }
    }

    fun setDnsWorkerCount(count: Int) {
        val safe = count.coerceIn(1, 32)
        _dnsState.update { it.copy(workerCount = safe) }
        viewModelScope.launch { settings.setDnsWorkerCount(safe) }
    }

    fun setDnsCategoryFilter(category: DnsProbe.ResolverCategory?) {
        _dnsState.update { it.copy(selectedCategory = category) }
    }

    fun setDnsSearchQuery(query: String) {
        _dnsState.update { it.copy(searchQuery = query) }
    }

    fun setDnsTargetDomain(domain: String) {
        val d = domain.trim().ifBlank { "digikala.com" }
        _dnsState.update { it.copy(targetDomain = d) }
    }

    fun addCustomResolver(name: String, ip: String): Boolean {
        val trimmedIp = ip.trim()
        if (!DnsProbe.isValidIp(trimmedIp)) return false
        val trimmedName = name.trim().ifBlank { trimmedIp }
        val newDef = DnsProbe.ResolverDef(name = trimmedName, ip = trimmedIp, category = DnsProbe.ResolverCategory.CUSTOM)
        val updatedList = (_dnsState.value.resolvers.filterNot { it.ip == trimmedIp } + newDef)
        _dnsState.update { it.copy(resolvers = updatedList, total = updatedList.size) }
        persistCustomResolvers(updatedList)
        return true
    }

    fun deleteCustomResolver(ip: String) {
        val updatedList = _dnsState.value.resolvers.filterNot { it.ip == ip && it.category == DnsProbe.ResolverCategory.CUSTOM }
        _dnsState.update {
            it.copy(
                resolvers = updatedList,
                results = it.results - ip,
                total = updatedList.size,
            )
        }
        persistCustomResolvers(updatedList)
    }

    fun importResolvers(text: String): Int {
        val newItems = text.lines().mapNotNull { DnsProbe.parseResolverLine(it) }.map {
            it.copy(category = DnsProbe.ResolverCategory.CUSTOM)
        }
        if (newItems.isEmpty()) return 0
        val existingIps = _dnsState.value.resolvers.map { it.ip }.toSet()
        val toAdd = newItems.filter { !existingIps.contains(it.ip) }.distinctBy { it.ip }
        if (toAdd.isNotEmpty()) {
            val updated = _dnsState.value.resolvers + toAdd
            _dnsState.update { it.copy(resolvers = updated, total = updated.size) }
            persistCustomResolvers(updated)
        }
        return toAdd.size
    }

    fun exportWorkingResolversText(): String {
        return _dnsState.value.results.values
            .filter { it.latencyMs != null }
            .sortedBy { it.latencyMs }
            .joinToString("\n") { "${it.name}|${it.ip}" }
    }

    private fun persistCustomResolvers(list: List<DnsProbe.ResolverDef>) {
        val customDefs = list.filter { it.category == DnsProbe.ResolverCategory.CUSTOM }
        val serialized = customDefs.joinToString("\n") { "CUSTOM|${it.name}|${it.ip}" }
        viewModelScope.launch { settings.setCustomDnsResolvers(serialized) }
    }

    /** Fastest answered resolver from the last test run, if any. */
    fun fastestDns(): DnsProbe.ProbeResult? =
        _dnsState.value.results.values.filter { it.latencyMs != null }.minByOrNull { it.latencyMs!! }

    fun applyDns(ip: String) {
        viewModelScope.launch { settings.setVpnDnsIp(ip) }
    }

    // Built-in open-site fallback domains from assets/cf_domains.txt. Kept apart
    // from the user's custom additions so the two can be merged on every edit.
    private var bundledDomains: List<String> = emptyList()

    init {
        com.ahoura.asha_scanner_ip.core.vpn.VpnManager.init(app)
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

    fun setTier(tier: com.ahoura.asha_scanner_ip.core.model.ScanTier) {
        if (tier == com.ahoura.asha_scanner_ip.core.model.ScanTier.CUSTOM) {
            updateScanConfig { it.copy(tier = tier) }
            return
        }
        updateScanConfig {
            when (tier) {
                com.ahoura.asha_scanner_ip.core.model.ScanTier.TURBO -> it.copy(
                    tier = tier, count = 1000, concurrency = 128, timeoutMs = 3000,
                    mode = com.ahoura.asha_scanner_ip.core.model.ProbeMode.TCP, speedTest = false
                )
                com.ahoura.asha_scanner_ip.core.model.ScanTier.BALANCED -> it.copy(
                    tier = tier, count = 500, concurrency = 64, timeoutMs = 5000,
                    mode = com.ahoura.asha_scanner_ip.core.model.ProbeMode.HTTP, speedTest = true
                )
                com.ahoura.asha_scanner_ip.core.model.ScanTier.THOROUGH -> it.copy(
                    tier = tier, count = 1500, concurrency = 96, timeoutMs = 7000,
                    mode = com.ahoura.asha_scanner_ip.core.model.ProbeMode.HTTP, speedTest = true
                )
                com.ahoura.asha_scanner_ip.core.model.ScanTier.STEALTH -> it.copy(
                    tier = tier, count = 300, concurrency = 20, timeoutMs = 10000,
                    mode = com.ahoura.asha_scanner_ip.core.model.ProbeMode.TLS, speedTest = true
                )
                com.ahoura.asha_scanner_ip.core.model.ScanTier.IRONCLAD -> it.copy(
                    tier = tier, count = 2000, concurrency = 128, timeoutMs = 15000,
                    mode = com.ahoura.asha_scanner_ip.core.model.ProbeMode.HTTP, speedTest = true,
                    top = 20, stabilityCount = 10
                )
                com.ahoura.asha_scanner_ip.core.model.ScanTier.CUSTOM -> it.copy(tier = tier)
            }
        }
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
                    val url = if (subServer.isListening) subServer.getUrl() else null
                    _state.update { it.copy(progress = p, subUrl = url) }
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

    /** Configure defaults for Quick Scan (ensures explicit test IPs are cleared). */
    fun prepareQuickScan() = updateScanConfig {
        it.copy(explicitIps = emptyList())
    }.also {
        _state.update { it.copy(testIpsText = "") }
    }

    /** Configure defaults for the Discover-Colos preset. */
    fun prepareDiscover() = updateScanConfig {
        it.copy(
            explicitIps = emptyList(), count = 300, tries = 2, timeoutMs = 5_000,
            mode = com.ahoura.asha_scanner_ip.core.model.ProbeMode.HTTP,
            speedTest = false, top = 50, ports = listOf(443),
        )
    }.also {
        _state.update { it.copy(testIpsText = "") }
    }

    // ---- VPN Client Controller ----

    fun startVpn(context: android.content.Context) {
        val transport = selectedTransport.value
        if (transport == "custom") {
            val profile = activeProfile.value
            if (profile != null) {
                startVpn(context, profile)
            }
        } else {
            startVpnTransport(context, transport)
        }
    }

    fun startVpnTransport(context: android.content.Context, transport: String) {
        setSelectedTransport(transport)
        com.ahoura.asha_scanner_ip.core.vpn.VpnManager.startTransport(context, transport)
    }

    fun startVpn(context: android.content.Context, profile: com.ahoura.asha_scanner_ip.core.vpn.VpnProfile) {
        setSelectedTransport("custom")
        com.ahoura.asha_scanner_ip.core.vpn.VpnManager.start(context, profile)
    }

    fun stopVpn(context: android.content.Context) {
        com.ahoura.asha_scanner_ip.core.vpn.VpnManager.stop(context)
    }

    fun addProfile(rawLink: String, customName: String? = null, cleanIp: String? = null) {
        viewModelScope.launch {
            runCatching { configStore.addProfile(rawLink, customName, cleanIp) }
        }
    }

    fun updateProfile(profile: com.ahoura.asha_scanner_ip.core.vpn.VpnProfile) {
        viewModelScope.launch { configStore.updateProfile(profile) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { configStore.deleteProfile(id) }
    }

    fun setActiveProfile(id: String) {
        viewModelScope.launch { configStore.setActiveProfileId(id) }
    }

    fun updateCleanIp(profileId: String, cleanIp: String?) {
        viewModelScope.launch { configStore.updateCleanIp(profileId, cleanIp) }
    }

    fun measurePing(profile: com.ahoura.asha_scanner_ip.core.vpn.VpnProfile) {
        viewModelScope.launch {
            val ms = com.ahoura.asha_scanner_ip.core.vpn.VpnManager.measurePing(profile)
            configStore.updatePing(profile.id, ms)
        }
    }

    fun setVpnBypassIr(value: Boolean) {
        viewModelScope.launch { settings.setVpnBypassIr(value) }
    }

    fun connectWithScannedIp(context: android.content.Context, cleanIp: String, proxy: ProxyConfig?) {
        viewModelScope.launch {
            val p = proxy ?: _state.value.parsedProxy ?: return@launch
            val profile = configStore.addProfile(
                rawLink = p.raw,
                customName = "${p.protocol.scheme.uppercase()} - $cleanIp",
                cleanIp = cleanIp,
            )
            startVpn(context, profile)
        }
    }
}
