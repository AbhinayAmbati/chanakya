package com.chanakya.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The single DataStore instance for user preferences. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "chanakya_settings")

/**
 * Persists the user's preferences. Backed by DataStore so reads are asynchronous and
 * safe to observe from the UI.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val BLOCK_IP_LEVEL = booleanPreferencesKey("block_ip_level")
        val ENABLED_BLOCKLISTS = stringSetPreferencesKey("enabled_blocklists")
    }

    /** Whether the local VPN should be running. */
    val protectionEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.PROTECTION_ENABLED] ?: false }

    /** Whether to restart protection automatically after a reboot. */
    val autoStartOnBoot: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.AUTO_START_ON_BOOT] ?: true }

    /** Whether IP-level (non-DNS) blocking is enabled. */
    val blockIpLevel: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.BLOCK_IP_LEVEL] ?: false }

    /** The set of enabled blocklist identifiers. */
    val enabledBlocklists: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[Keys.ENABLED_BLOCKLISTS] ?: emptySet() }

    /** Enable or disable protection. */
    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PROTECTION_ENABLED] = enabled }
    }

    /** Enable or disable auto-start on boot. */
    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_START_ON_BOOT] = enabled }
    }

    /** Enable or disable IP-level blocking. */
    suspend fun setBlockIpLevel(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BLOCK_IP_LEVEL] = enabled }
    }

    /** Replace the set of enabled blocklists. */
    suspend fun setEnabledBlocklists(ids: Set<String>) {
        context.settingsDataStore.edit { it[Keys.ENABLED_BLOCKLISTS] = ids }
    }
}
