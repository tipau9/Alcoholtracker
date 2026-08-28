package de.tipau.promille.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.tipau.promille.bac.BacProjectionInput
import java.util.Locale
import kotlin.math.max

/**
 * Port of NotificationService.swift.
 * Manages instant alerts (SOS/high BAC) and scheduled sobriety/threshold alarms.
 */
object NotificationService {

    /** Friend SOS and friend high-permille alerts. */
    const val CHANNEL_ALERTS = "promille.alerts"
    /** Scheduled sobriety and threshold notifications. */
    const val CHANNEL_SOBRIETY = "promille.sobriety"

    const val SOBER_NOTIFICATION_ID = "sober_notification"
    const val DRIVE_NOTIFICATION_ID = "drive_notification"
    private const val PREFS_NAME = "promille_notifications"
    private const val KEY_SOBRIETY_ENABLED = "notifySobrietyEnabled"

    const val SOBER_REQ_CODE = 1001
    const val DRIVE_REQ_CODE = 1002

    private var channelsCreated = false

    private fun ensureChannels(context: Context) {
        if (channelsCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Crew-Warnungen",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "SOS und hohe Promillewerte deiner Freunde."
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SOBRIETY,
                    "Nüchternheits-Meldungen",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Meldungen, wenn du wieder nüchtern oder unter deiner Warnschwelle bist."
                }
            )
        }
        channelsCreated = true
    }

    fun isAuthorized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isSobrietyNotificationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOBRIETY_ENABLED, true)

    fun setSobrietyNotificationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOBRIETY_ENABLED, enabled)
            .apply()
    }

    /**
     * Immediate local notification. [id] is stable per friend and per kind, so a
     * repeat replaces the earlier one instead of stacking a second banner.
     */
    fun notifyNow(context: Context, id: String, title: String, body: String) {
        if (!isAuthorized(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }

    data class ScheduledNotificationPlan(
        val id: String,
        val requestCode: Int,
        val delaySeconds: Long,
        val title: String,
        val body: String
    )

    fun promilleString(value: Double): String =
        String.format(Locale.GERMAN, "%.2f", value)

    /**
     * Pure planning function without platform dependencies, matching NotificationService.swift.
     */
    fun planNotifications(
        input: BacProjectionInput,
        tipsyThreshold: Double,
        warningThreshold: Double,
        nowEpochSeconds: Long
    ): List<ScheduledNotificationPlan> {
        val plans = mutableListOf<ScheduledNotificationPlan>()

        // Sober notification
        val soberHours = input.hoursUntil(tipsyThreshold, nowEpochSeconds)
        if (soberHours != null && soberHours > 0.05) {
            val delaySec = max(60L, (soberHours * 3600).toLong())
            plans.add(
                ScheduledNotificationPlan(
                    id = SOBER_NOTIFICATION_ID,
                    requestCode = SOBER_REQ_CODE,
                    delaySeconds = delaySec,
                    title = "Wieder nüchtern",
                    body = "Dein Promillewert liegt jetzt rechnerisch bei unter ${promilleString(tipsyThreshold)} ‰. Schätzung, kein Messwert."
                )
            )
        }

        // Warning threshold notification
        val driveHours = input.hoursUntil(warningThreshold, nowEpochSeconds)
        if (driveHours != null && driveHours > 0.05) {
            val delaySec = max(60L, (driveHours * 3600).toLong())
            plans.add(
                ScheduledNotificationPlan(
                    id = DRIVE_NOTIFICATION_ID,
                    requestCode = DRIVE_REQ_CODE,
                    delaySeconds = delaySec,
                    title = "Unter deiner Warnschwelle",
                    body = "Dein Wert liegt jetzt rechnerisch unter ${promilleString(warningThreshold)} ‰. Keine Garantie für Fahrtauglichkeit."
                )
            )
        }

        return plans
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val soberIntent = PendingIntent.getBroadcast(
            context, SOBER_REQ_CODE,
            Intent(context, PromilleNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val driveIntent = PendingIntent.getBroadcast(
            context, DRIVE_REQ_CODE,
            Intent(context, PromilleNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(soberIntent)
        alarmManager.cancel(driveIntent)
    }

    fun reschedule(
        context: Context,
        input: BacProjectionInput,
        tipsyThreshold: Double,
        warningThreshold: Double
    ) {
        cancelAll(context)

        if (!isAuthorized(context) || !isSobrietyNotificationEnabled(context)) return
        ensureChannels(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis() / 1000
        val plans = planNotifications(input, tipsyThreshold, warningThreshold, now)

        for (plan in plans) {
            val intent = Intent(context, PromilleNotificationReceiver::class.java).apply {
                putExtra("id", plan.id)
                putExtra("title", plan.title)
                putExtra("body", plan.body)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                plan.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = System.currentTimeMillis() + (plan.delaySeconds * 1000)
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                120_000L,
                pendingIntent
            )
        }
    }
}
