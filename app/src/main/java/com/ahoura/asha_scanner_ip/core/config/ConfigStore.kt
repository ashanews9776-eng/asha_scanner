package com.ahoura.asha_scanner_ip.core.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import com.ahoura.asha_scanner_ip.core.vpn.VpnProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.configDataStore by preferencesDataStore(name = "asha_vpn_configs")

/**
 * Manages persistence for imported VPN profiles and active selection.
 */
class ConfigStore(private val context: Context) {

    private val keyProfilesJson = stringPreferencesKey("profiles_json")
    private val keyActiveProfileId = stringPreferencesKey("active_profile_id")

    val profiles: Flow<List<VpnProfile>> = context.configDataStore.data.map { prefs ->
        val jsonStr = prefs[keyProfilesJson] ?: "[]"
        deserializeProfiles(jsonStr)
    }

    val activeProfileId: Flow<String?> = context.configDataStore.data.map { prefs ->
        prefs[keyActiveProfileId]
    }

    val activeProfile: Flow<VpnProfile?> = combine(profiles, activeProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    suspend fun addProfile(rawLink: String, customName: String? = null, cleanIp: String? = null): VpnProfile {
        val proxy = ProxyParser.parse(rawLink)
        val name = customName?.ifBlank { null } ?: proxy.remark.ifBlank { "${proxy.protocol.scheme.uppercase()} - ${proxy.address}" }
        val profile = VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            raw = rawLink.trim(),
            proxy = proxy,
            cleanIp = cleanIp?.ifBlank { null },
        )
        context.configDataStore.edit { prefs ->
            val list = deserializeProfiles(prefs[keyProfilesJson] ?: "[]").toMutableList()
            list.add(0, profile)
            prefs[keyProfilesJson] = serializeProfiles(list)
            if (prefs[keyActiveProfileId].isNullOrBlank()) {
                prefs[keyActiveProfileId] = profile.id
            }
        }
        return profile
    }

    suspend fun updateProfile(profile: VpnProfile) {
        context.configDataStore.edit { prefs ->
            val list = deserializeProfiles(prefs[keyProfilesJson] ?: "[]").toMutableList()
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) {
                list[idx] = profile
                prefs[keyProfilesJson] = serializeProfiles(list)
            }
        }
    }

    suspend fun deleteProfile(id: String) {
        context.configDataStore.edit { prefs ->
            val list = deserializeProfiles(prefs[keyProfilesJson] ?: "[]").toMutableList()
            list.removeAll { it.id == id }
            prefs[keyProfilesJson] = serializeProfiles(list)
            if (prefs[keyActiveProfileId] == id) {
                prefs[keyActiveProfileId] = list.firstOrNull()?.id ?: ""
            }
        }
    }

    suspend fun setActiveProfileId(id: String) {
        context.configDataStore.edit { prefs ->
            prefs[keyActiveProfileId] = id
        }
    }

    suspend fun updateCleanIp(profileId: String, cleanIp: String?) {
        context.configDataStore.edit { prefs ->
            val list = deserializeProfiles(prefs[keyProfilesJson] ?: "[]").toMutableList()
            val idx = list.indexOfFirst { it.id == profileId }
            if (idx >= 0) {
                list[idx] = list[idx].copy(cleanIp = cleanIp?.ifBlank { null })
                prefs[keyProfilesJson] = serializeProfiles(list)
            }
        }
    }

    suspend fun updatePing(profileId: String, pingMs: Long?) {
        context.configDataStore.edit { prefs ->
            val list = deserializeProfiles(prefs[keyProfilesJson] ?: "[]").toMutableList()
            val idx = list.indexOfFirst { it.id == profileId }
            if (idx >= 0) {
                list[idx] = list[idx].copy(pingMs = pingMs)
                prefs[keyProfilesJson] = serializeProfiles(list)
            }
        }
    }

    private fun serializeProfiles(list: List<VpnProfile>): String {
        val array = JSONArray()
        for (p in list) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("raw", p.raw)
            if (p.cleanIp != null) obj.put("cleanIp", p.cleanIp)
            if (p.pingMs != null) obj.put("pingMs", p.pingMs)
            obj.put("timestamp", p.timestamp)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeProfiles(json: String): List<VpnProfile> {
        val result = mutableListOf<VpnProfile>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val raw = obj.getString("raw")
                val proxy = runCatching { ProxyParser.parse(raw) }.getOrNull() ?: continue
                result.add(
                    VpnProfile(
                        id = obj.getString("id"),
                        name = obj.optString("name", proxy.remark),
                        raw = raw,
                        proxy = proxy,
                        cleanIp = if (obj.has("cleanIp")) obj.getString("cleanIp") else null,
                        pingMs = if (obj.has("pingMs")) obj.getLong("pingMs") else null,
                        timestamp = obj.optLong("timestamp", 0L),
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }
}
