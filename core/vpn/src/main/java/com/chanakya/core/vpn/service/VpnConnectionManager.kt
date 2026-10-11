package com.chanakya.core.vpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.chanakya.core.vpn.model.VpnState
import com.chanakya.core.vpn.model.VpnStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The UI-facing entry point for controlling the local VPN.
 *
 * It hides the details of starting/stopping the service and exposing its state, so
 * ViewModels never touch `Intent`s or the service class directly.
 */
class VpnConnectionManager(private val context: Context) {

    /** The current VPN state, observable by the UI. */
    val state: StateFlow<VpnState> = VpnStatus.state

    /**
     * The system consent intent that must be shown before the VPN can start, or null
     * if consent has already been granted.
     */
    fun consentIntent(): Intent? = VpnService.prepare(context)

    /** Ask the service to start protecting. */
    fun start() {
        val intent = Intent(context, ChanakyaVpnService::class.java).setAction(ChanakyaVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Ask the service to stop protecting. */
    fun stop() {
        val intent = Intent(context, ChanakyaVpnService::class.java).setAction(ChanakyaVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
