package com.chanakya.core.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.chanakya.core.vpn.R

/**
 * Builds the ongoing notification that Android requires for a foreground service.
 */
class VpnNotification(private val context: Context) {

    /**
     * Create (once) the notification channel and return the ongoing notification.
     *
     * @param blockedCount how many trackers have been blocked, shown in the body.
     */
    fun build(blockedCount: Int): Notification {
        createChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.vpn_notification_title))
            .setContentText(context.getString(R.string.vpn_notification_text, blockedCount))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Register the channel with the system (safe to call repeatedly). */
    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.vpn_notification_channel_description) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** Notification channel id. */
        const val CHANNEL_ID = "chanakya_protection"

        /** Notification id used with startForeground. */
        const val NOTIFICATION_ID = 1001
    }
}
