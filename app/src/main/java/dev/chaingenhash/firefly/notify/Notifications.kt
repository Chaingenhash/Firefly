package dev.chaingenhash.firefly.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.chaingenhash.firefly.R
import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.Threshold
import dev.chaingenhash.firefly.ui.MainActivity

private const val TAG = "Firefly"

/** Title of the alert for [threshold] at [level]. Pure, so it is unit-tested directly. */
fun alertTitle(threshold: Threshold, level: Int): String =
    threshold.label ?: when (threshold.direction) {
        Direction.CHARGING_UP -> "Battery reached $level% — time to unplug"
        Direction.DISCHARGING_DOWN -> "Battery down to $level% — time to charge"
    }

/** Body of the ongoing status notification. Pure, so it is unit-tested directly. */
fun statusText(state: BatteryState?): String = when {
    state == null -> "Waiting for battery reading…"
    state.plugged -> "${state.level}% · Charging"
    else -> "${state.level}% · On battery"
}

/**
 * Displaces a notification id that would collide with the ongoing status notification.
 * An alert must never post under [Notifications.STATUS_NOTIFICATION_ID], or it would
 * replace the status notification in the shade instead of appearing beside it.
 */
fun displaceFromStatusId(hash: Int): Int =
    if (hash == Notifications.STATUS_NOTIFICATION_ID) hash + 1 else hash

/** Notification id for [threshold]'s alerts. Never [Notifications.STATUS_NOTIFICATION_ID]. */
fun alertNotificationId(threshold: Threshold): Int =
    displaceFromStatusId(threshold.id.hashCode())

object Notifications {

    const val CHANNEL_STATUS = "status"
    const val CHANNEL_ALERTS = "alerts"
    const val STATUS_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.channel_status_description)
            setShowBadge(false)
        }

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_description)
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(status, alerts))
    }

    fun buildStatus(context: Context, state: BatteryState?): Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle(context.getString(R.string.status_title))
            .setContentText(statusText(state))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(context))
            .build()

    @SuppressLint("MissingPermission")   // guarded by areNotificationsEnabled() below
    fun alert(context: Context, threshold: Threshold, level: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled; dropping alert for threshold ${threshold.id}")
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle(alertTitle(threshold, level))
            .setContentText("Battery is now at $level%")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

        manager.notify(alertNotificationId(threshold), notification)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
