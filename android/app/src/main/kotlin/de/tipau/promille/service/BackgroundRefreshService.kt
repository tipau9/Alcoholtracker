package de.tipau.promille.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Background refresh coordination service matching iOS BackgroundRefreshService.swift 1:1.
 *
 * Coordinates periodic background tasks:
 * 1. Widget timeline / app widget reload (every 15 min).
 * 2. Supabase offline sync flush (every 30 min).
 */
object BackgroundRefreshService {
    const val WIDGET_REFRESH_ACTION = "de.tipau.Promille.widgetRefresh"
    const val SUPABASE_SYNC_ACTION = "de.tipau.Promille.supabaseSync"

    var onSupabaseSync: (suspend () -> Unit)? = null
    var onWidgetRefresh: (() -> Unit)? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerAndSchedule(context: Context) {
        scheduleWidgetRefresh(context)
        scheduleSupabaseSync(context)
    }

    fun scheduleWidgetRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BackgroundRefreshReceiver::class.java).apply {
            action = WIDGET_REFRESH_ACTION
        }
        val pending = PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 15 minutes window
        val triggerAt = SystemClock.elapsedRealtime() + 15 * 60 * 1000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
    }

    fun scheduleSupabaseSync(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BackgroundRefreshReceiver::class.java).apply {
            action = SUPABASE_SYNC_ACTION
        }
        val pending = PendingIntent.getBroadcast(
            context,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 30 minutes window
        val triggerAt = SystemClock.elapsedRealtime() + 30 * 60 * 1000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
    }

    fun handleWidgetRefresh(context: Context) {
        scheduleWidgetRefresh(context)
        onWidgetRefresh?.invoke()
    }

    fun handleSupabaseSync(context: Context) {
        scheduleSupabaseSync(context)
        val syncAction = onSupabaseSync
        if (syncAction != null) {
            serviceScope.launch {
                try {
                    syncAction()
                } catch (_: Throwable) {}
            }
        }
    }
}

class BackgroundRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BackgroundRefreshService.WIDGET_REFRESH_ACTION -> {
                BackgroundRefreshService.handleWidgetRefresh(context)
            }
            BackgroundRefreshService.SUPABASE_SYNC_ACTION -> {
                BackgroundRefreshService.handleSupabaseSync(context)
            }
        }
    }
}
