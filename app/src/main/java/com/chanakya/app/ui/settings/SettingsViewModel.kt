package com.chanakya.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.prefs.SettingsRepository
import com.chanakya.core.vpn.service.VpnConnectionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen: exposes the persisted preferences and their setters.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val vpnConnectionManager: VpnConnectionManager,
) : ViewModel() {

    /** Whether to restart protection after a reboot. */
    val autoStartOnBoot: StateFlow<Boolean> = settingsRepository.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Whether IP-level blocking is enabled. */
    val blockIpLevel: StateFlow<Boolean> = settingsRepository.blockIpLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Persist the auto-start preference. */
    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStartOnBoot(enabled) }
    }

    /** Persist the IP-level blocking preference. */
    fun setBlockIpLevel(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockIpLevel(enabled) }
    }
}
