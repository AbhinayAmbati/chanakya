package com.chanakya.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chanakya.core.data.prefs.SettingsRepository
import com.chanakya.core.vpn.service.VpnConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts protection after the device boots, when the user has asked for it.
 *
 * Protection is resumed only when **both** "start on boot" and the previous protection
 * state were on - rebooting should not switch protection back on for someone who had
 * deliberately turned it off.
 *
 * The VPN consent dialog cannot be shown from a receiver, so this proceeds only when
 * consent was already granted; otherwise the user is left to start protection manually.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // A receiver must not block, so finish asynchronously.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context)
                val autoStart = settings.autoStartOnBoot.first()
                val wasEnabled = settings.protectionEnabled.first()
                if (!autoStart || !wasEnabled) return@launch

                val manager = VpnConnectionManager(context)
                // prepare() returns null only when consent was already granted.
                if (manager.consentIntent() == null) manager.start()
            } catch (exception: Exception) {
                // Nothing useful to do from a receiver; the user can start it manually.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
