package com.chanakya.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.app.work.ReportScheduler
import com.chanakya.core.data.model.BlocklistInfo
import com.chanakya.core.data.model.ThemeMode
import com.chanakya.core.data.model.UserRule
import com.chanakya.core.data.prefs.SettingsRepository
import com.chanakya.core.data.repository.BlocklistRepository
import com.chanakya.core.data.repository.TrackerRepository
import com.chanakya.core.data.repository.UserRuleRepository
import com.chanakya.core.vpn.model.VpnDiagnostics
import com.chanakya.core.vpn.model.VpnState
import com.chanakya.core.vpn.model.VpnStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen: protection options, blocklist management, user rules and
 * the advertising-ID tools.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val blocklistRepository: BlocklistRepository,
    private val userRuleRepository: UserRuleRepository,
    private val trackerRepository: TrackerRepository,
    private val reportScheduler: ReportScheduler,
) : ViewModel() {

    /** Whether to restart protection after a reboot. */
    val autoStartOnBoot: StateFlow<Boolean> = settingsRepository.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Whether IP-level blocking is enabled. */
    val blockIpLevel: StateFlow<Boolean> = settingsRepository.blockIpLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether every packet is routed through the tunnel, not just DNS. */
    val fullTunnel: StateFlow<Boolean> = settingsRepository.fullTunnel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the periodic advertising-ID reminder is enabled. */
    val adIdReminder: StateFlow<Boolean> = settingsRepository.adIdReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Every blocklist with its current state. */
    val blocklists: StateFlow<List<BlocklistInfo>> = blocklistRepository.observeAvailableLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The user's allow/block rules. */
    val userRules: StateFlow<List<UserRule>> = userRuleRepository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which colour scheme the user has chosen. */
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** The current tunnel state, for the diagnostics view. */
    val vpnState: StateFlow<VpnState> = VpnStatus.state

    /** What the engine is doing, for the diagnostics view. */
    val diagnostics: StateFlow<VpnDiagnostics> = VpnStatus.diagnostics

    /** Blocked queries of all time, for the diagnostics view. */
    val blockedTotal: StateFlow<Int> = trackerRepository.observeProtectionSummary()
        .map { it.blockedTotal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // Make sure the default lists exist before the UI renders them.
        viewModelScope.launch { blocklistRepository.ensureInitialised() }
    }

    /** Persist the auto-start preference. */
    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStartOnBoot(enabled) }
    }

    /** Persist the IP-level blocking preference. */
    fun setBlockIpLevel(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockIpLevel(enabled) }
    }

    /** Persist the full-tunnel preference. */
    fun setFullTunnel(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFullTunnel(enabled) }
    }

    /** Toggle the advertising-ID reminder and (de)register its worker. */
    fun setAdIdReminder(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAdIdReminder(enabled)
            reportScheduler.setAdIdReminder(enabled)
        }
    }

    /** Turn a single blocklist on or off. */
    fun setBlocklistEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.currentEnabledBlocklists()
            settingsRepository.setEnabledBlocklists(
                if (enabled) current + id else current - id,
            )
        }
    }

    /** Import a blocklist from a document the user picked. */
    fun importBlocklist(uri: Uri) {
        viewModelScope.launch { blocklistRepository.importFromUri(uri) }
    }

    /** Persist the chosen colour scheme. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** Enable or disable a user rule without deleting it. */
    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { userRuleRepository.setEnabled(id, enabled) }
    }

    /** Delete a user rule. */
    fun deleteRule(id: Long) {
        viewModelScope.launch { userRuleRepository.delete(id) }
    }
}
