package com.chanakya.app.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.model.AppUsageSummary
import com.chanakya.core.data.model.ProtectionSummary
import com.chanakya.core.data.prefs.SettingsRepository
import com.chanakya.core.data.repository.TrackerRepository
import com.chanakya.core.vpn.model.VpnState
import com.chanakya.core.vpn.service.VpnConnectionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The state rendered by the Home screen.
 *
 * @param summary device-wide protection totals.
 * @param topOffenders the busiest apps, limited to a handful for the dashboard.
 * @param vpnState the current tunnel state.
 * @param protectionEnabled the user's persisted preference.
 */
data class HomeUiState(
    val summary: ProtectionSummary,
    val topOffenders: List<AppUsageSummary>,
    val vpnState: VpnState,
    val protectionEnabled: Boolean,
) {
    /** Whether the tunnel is currently up. */
    val isProtected: Boolean get() = vpnState is VpnState.Active
}

/**
 * Drives the Home dashboard by combining the repository's summary, the top apps and
 * the live VPN state.
 */
class HomeViewModel(
    private val trackerRepository: TrackerRepository,
    private val vpnConnectionManager: VpnConnectionManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** The UI state, recomputed whenever any source flow changes. */
    val uiState: StateFlow<HomeUiState> = combine(
        trackerRepository.observeProtectionSummary(),
        trackerRepository.observeAppSummaries(),
        vpnConnectionManager.state,
        settingsRepository.protectionEnabled,
    ) { summary, apps, vpnState, enabled ->
        HomeUiState(
            summary = summary,
            topOffenders = apps.take(3),
            vpnState = vpnState,
            protectionEnabled = enabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(
            summary = ProtectionSummary(0, 0, 0, null),
            topOffenders = emptyList(),
            vpnState = VpnState.Stopped,
            protectionEnabled = false,
        ),
    )

    /**
     * The system consent intent that must be shown before the VPN can start, or null
     * if consent was already granted.
     */
    fun consentIntent(): Intent? = vpnConnectionManager.consentIntent()

    /** Start protection and persist the preference. */
    fun startProtection() {
        vpnConnectionManager.start()
        viewModelScope.launch { settingsRepository.setProtectionEnabled(true) }
    }

    /** Stop protection and persist the preference. */
    fun stopProtection() {
        vpnConnectionManager.stop()
        viewModelScope.launch { settingsRepository.setProtectionEnabled(false) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
