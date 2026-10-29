package com.ahoura.asha_scanner_ip.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.emptyPreferences
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "asha_settings")

/** Persists lightweight user preferences (language, VPN routing options). */
class SettingsStore(private val context: Context) {
    private val keyLang = stringPreferencesKey("lang")
    private val keyVpnBypassIr = booleanPreferencesKey("vpn_bypass_ir")
    private val keyVpnDnsIp = stringPreferencesKey("vpn_dns_ip")
    private val keyCustomDns = stringPreferencesKey("custom_dns_resolvers")
    private val keyDnsWorkerCount = androidx.datastore.preferences.core.intPreferencesKey("dns_worker_count")
    private val keyStormPresetId = stringPreferencesKey("storm_tune_preset_id")
    private val keyAether24 = stringPreferencesKey("aether_v24_settings")
    private val keyScanGentle = booleanPreferencesKey("scan_gentle")
    private val keyScanPhase2Only = booleanPreferencesKey("scan_phase2_only")
    private val keyScanPastedTargets = stringPreferencesKey("scan_pasted_targets")
    private val keyAntiDpiEnabled = booleanPreferencesKey("anti_dpi_enabled")
    private val keyAntiDpiFinalmask = stringPreferencesKey("anti_dpi_finalmask")
    private val keyAntiDpiFingerprint = stringPreferencesKey("anti_dpi_fingerprint")
    private val keyAntiDpiAlpn = stringPreferencesKey("anti_dpi_alpn")
    private val keyAntiDpiCipherSuites = stringPreferencesKey("anti_dpi_cipher_suites")
    private val keyQuicBlock = booleanPreferencesKey("guard_quic_block")
    private val keyWebRtcBlock = booleanPreferencesKey("guard_webrtc_block")
    private val keyIpv6Block = booleanPreferencesKey("guard_ipv6_block")
    private val keyDnsSinkhole = booleanPreferencesKey("guard_dns_sinkhole")

    private val safePrefsFlow: Flow<androidx.datastore.preferences.core.Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                emit(emptyPreferences())
            }
        }

    val language: Flow<Lang> = safePrefsFlow.map { prefs ->
        when (prefs[keyLang]) {
            Lang.FA.code -> Lang.FA
            else -> Lang.EN   // default to English; Persian is opt-in via the toggle
        }
    }

    // Split tunneling for Iranian services — ON by default: local banks,
    // government portals and .ir sites break when reached from a foreign exit
    // IP, and bypassing them conserves proxy bandwidth.
    val vpnBypassIr: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyVpnBypassIr] ?: true
    }

    // DNS server for the VPN session (TUN + xray), chosen by the DNS Tuner
    // from live probes. Falls back to Cloudflare when never set.
    val vpnDnsIp: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyVpnDnsIp] ?: "1.1.1.1"
    }

    val customDnsResolvers: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyCustomDns].orEmpty()
    }

    val dnsWorkerCount: Flow<Int> = safePrefsFlow.map { prefs ->
        prefs[keyDnsWorkerCount] ?: 8
    }

    // StormDNS auto-tune preset applied at connect; "iran-average" until the
    // tuner measures a faster one on this carrier.
    val stormPresetId: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyStormPresetId] ?: "iran-average"
    }

    suspend fun setLanguage(lang: Lang) {
        runCatching {
            context.dataStore.edit { it[keyLang] = lang.code }
        }
    }

    suspend fun setVpnBypassIr(value: Boolean) {
        runCatching {
            context.dataStore.edit { it[keyVpnBypassIr] = value }
        }
    }

    suspend fun setVpnDnsIp(value: String) {
        runCatching {
            context.dataStore.edit { it[keyVpnDnsIp] = value }
        }
    }

    suspend fun setCustomDnsResolvers(text: String) {
        runCatching {
            context.dataStore.edit { it[keyCustomDns] = text }
        }
    }

    suspend fun setDnsWorkerCount(workers: Int) {
        runCatching {
            context.dataStore.edit { it[keyDnsWorkerCount] = workers.coerceIn(1, 32) }
        }
    }

    suspend fun setStormPresetId(id: String) {
        runCatching {
            context.dataStore.edit { it[keyStormPresetId] = id }
        }
    }

    // ── Aether v2.4.0 CLI transport settings (one JSON blob, one key) ──

    val aether24Json: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyAether24] ?: "{}"
    }

    suspend fun setAether24Json(json: String) {
        runCatching {
            context.dataStore.edit { it[keyAether24] = json }
        }
    }

    val scanGentle: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyScanGentle] ?: false
    }

    val scanPhase2Only: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyScanPhase2Only] ?: false
    }

    val scanPastedTargets: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyScanPastedTargets].orEmpty()
    }

    val antiDpiEnabled: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyAntiDpiEnabled] ?: true
    }

    val antiDpiFinalmask: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyAntiDpiFinalmask] ?: com.ahoura.asha_scanner_ip.core.model.AntiDpiConfig.DEFAULT_FINALMASK
    }

    val antiDpiFingerprint: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyAntiDpiFingerprint] ?: "unsafe"
    }

    val antiDpiAlpn: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyAntiDpiAlpn] ?: "http/1.1"
    }

    val antiDpiCipherSuites: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[keyAntiDpiCipherSuites] ?: com.ahoura.asha_scanner_ip.core.model.AntiDpiConfig.DEFAULT_CIPHERS
    }

    suspend fun setScanGentle(value: Boolean) {
        runCatching { context.dataStore.edit { it[keyScanGentle] = value } }
    }

    suspend fun setScanPhase2Only(value: Boolean) {
        runCatching { context.dataStore.edit { it[keyScanPhase2Only] = value } }
    }

    suspend fun setScanPastedTargets(value: String) {
        runCatching { context.dataStore.edit { it[keyScanPastedTargets] = value } }
    }

    suspend fun setAntiDpiEnabled(value: Boolean) {
        runCatching { context.dataStore.edit { it[keyAntiDpiEnabled] = value } }
    }

    suspend fun setAntiDpiFinalmask(value: String) {
        runCatching { context.dataStore.edit { it[keyAntiDpiFinalmask] = value } }
    }

    suspend fun setAntiDpiFingerprint(value: String) {
        runCatching { context.dataStore.edit { it[keyAntiDpiFingerprint] = value } }
    }

    suspend fun setAntiDpiAlpn(value: String) {
        runCatching { context.dataStore.edit { it[keyAntiDpiAlpn] = value } }
    }

    suspend fun setAntiDpiCipherSuites(value: String) {
        runCatching { context.dataStore.edit { it[keyAntiDpiCipherSuites] = value } }
    }

    val quicBlock: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyQuicBlock] ?: true
    }

    val webRtcBlock: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyWebRtcBlock] ?: true
    }

    val ipv6Block: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyIpv6Block] ?: true
    }

    val dnsSinkhole: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[keyDnsSinkhole] ?: true
    }

    suspend fun setQuicBlock(value: Boolean) {
        runCatching { context.dataStore.edit { it[keyQuicBlock] = value } }
    }

    suspend fun setWebRtcBlock(value: Boolean) {
        runCatching { context.dataStore.edit { it[keyWebRtcBlock] = value } }
    }

    suspend fun setIpv6Block(value: Boolean) {
        runCatching { context.dataStore.edit { it[keyIpv6Block] = value } }
    }

    suspend fun setDnsSinkhole(value: Boolean) {
        runCatching { context.dataStore.edit { it[keyDnsSinkhole] = value } }
    }
}
