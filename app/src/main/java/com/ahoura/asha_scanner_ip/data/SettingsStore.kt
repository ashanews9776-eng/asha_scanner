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
}
