package com.chanakya.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chanakya.app.R

/**
 * Builds and posts the notifications used by the background workers.
 *
 * Two channels are used so the user can silence the daily summary without also losing
 * the ad-ID reminder, and vice versa.
 */
class ChanakyaNotifications(private val context: Context) {

    /** Post the daily protection summary. */
    fun showReport(blockedCount: Int, appCount: Int, topApp: String?) {
        ensureChannel(REPORT_CHANNEL_ID, context.getString(R.string.report_channel_name))

        val body = if (blockedCount == 0) {
            context.getString(R.string.report_body_quiet)
        } else {
            context.getString(R.string.report_body, blockedCount, appCount)
        }
        val text = buildString {
            append(body)
            if (!topApp.isNullOrBlank()) {
                append(" - ")
                append(context.getString(R.string.report_top_app, topApp))
            }
        }

        post(REPORT_NOTIFICATION_ID, context.getString(R.string.report_title), text, REPORT_CHANNEL_ID)
    }

    /** Post the periodic reminder to reset the advertising ID. */
    fun showAdIdReminder() {
        ensureChannel(AD_ID_CHANNEL_ID, context.getString(R.string.ad_id_channel_name))
        post(
            AD_ID_NOTIFICATION_ID,
            context.getString(R.string.ad_id_reminder_title),
            context.getString(R.string.ad_id_reminder_body),
            AD_ID_CHANNEL_ID,
        )
    }

    /** Register a channel if it does not already exist. */
    private fun ensureChannel(id: String, name: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** Post a notification, tolerating a missing permission on Android 13+. */
    private fun post(id: Int, title: String, text: String, channelId: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (exception: SecurityException) {
            // POST_NOTIFICATIONS was not granted; nothing to do.
        }
    }

    private companion object {
        const val REPORT_CHANNEL_ID = "chanakya_reports"
        const val AD_ID_CHANNEL_ID = "chanakya_ad_id"
        const val REPORT_NOTIFICATION_ID = 2001
        const val AD_ID_NOTIFICATION_ID = 2002
    }
}
