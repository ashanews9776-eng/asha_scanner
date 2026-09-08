package com.ahoura.asha_scanner_ip.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "asha_settings")

/** Persists lightweight user preferences (language, VPN routing options). */
class SettingsStore(private val context: Context) {
    private val keyLang = stringPreferencesKey("lang")
    private val keyVpnBypassIr = booleanPreferencesKey("vpn_bypass_ir")

    val language: Flow<Lang> = context.dataStore.data.map { prefs ->
        when (prefs[keyLang]) {
            Lang.FA.code -> Lang.FA
            else -> Lang.EN   // default to English; Persian is opt-in via the toggle
        }
    }

    // Split tunneling for Iranian services — ON by default: local banks,
    // government portals and .ir sites break when reached from a foreign exit
    // IP, and bypassing them conserves proxy bandwidth.
    val vpnBypassIr: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyVpnBypassIr] ?: true
    }

    suspend fun setLanguage(lang: Lang) {
        context.dataStore.edit { it[keyLang] = lang.code }
    }

    suspend fun setVpnBypassIr(value: Boolean) {
        context.dataStore.edit { it[keyVpnBypassIr] = value }
    }
}
