package com.chanakya.core.vpn.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A process-wide holder for the current [VpnState] and [VpnDiagnostics].
 *
 * The service and the UI run in the same process, so a small singleton is a simpler
 * and more predictable bridge between them than a binder. The service writes; the UI
 * observes.
 */
object VpnStatus {

    private val mutableState = MutableStateFlow<VpnState>(VpnState.Stopped)

    /** The current VPN state, observable by the UI. */
    val state: StateFlow<VpnState> = mutableState.asStateFlow()

    private val mutableDiagnostics = MutableStateFlow(VpnDiagnostics())

    /** What the engine is doing, observable by the UI. */
    val diagnostics: StateFlow<VpnDiagnostics> = mutableDiagnostics.asStateFlow()

    /** Update the state. Called only by the VPN service. */
    internal fun update(newState: VpnState) {
        mutableState.value = newState
    }

    /** Replace the diagnostics snapshot. Called only by the VPN service. */
    internal fun updateDiagnostics(newDiagnostics: VpnDiagnostics) {
        mutableDiagnostics.value = newDiagnostics
    }

    /** Merge changes into the diagnostics snapshot. Called only by the VPN service. */
    internal fun editDiagnostics(transform: (VpnDiagnostics) -> VpnDiagnostics) {
        mutableDiagnostics.update(transform)
    }
}
