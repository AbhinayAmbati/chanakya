package com.chanakya.core.vpn.model

/**
 * The lifecycle state of the local VPN, exposed to the UI.
 */
sealed interface VpnState {

    /** The tunnel is not running. */
    data object Stopped : VpnState

    /** The tunnel is being established. */
    data object Starting : VpnState

    /** The tunnel is up and filtering traffic. */
    data class Active(val since: Long) : VpnState

    /** The tunnel stopped because of an error. */
    data class Error(val message: String) : VpnState
}
