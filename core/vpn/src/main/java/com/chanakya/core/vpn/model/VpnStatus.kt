package com.chanakya.core.vpn.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A process-wide holder for the current [VpnState].
 *
 * The service and the UI run in the same process, so a small singleton is a simpler
 * and more predictable bridge between them than a binder. The service writes; the UI
 * observes.
 */
object VpnStatus {

    private val mutableState = MutableStateFlow<VpnState>(VpnState.Stopped)

    /** The current VPN state, observable by the UI. */
    val state: StateFlow<VpnState> = mutableState.asStateFlow()

    /** Update the state. Called only by the VPN service. */
    internal fun update(newState: VpnState) {
        mutableState.value = newState
    }
}
