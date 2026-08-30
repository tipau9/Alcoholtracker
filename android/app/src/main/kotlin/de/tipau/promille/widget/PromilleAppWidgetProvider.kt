package de.tipau.promille.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import de.tipau.promille.MainActivity
import de.tipau.promille.R
import de.tipau.promille.service.SharedStateStore
import java.util.Locale

/**
 * 1:1 Port of iOS PromilleWidget (WSmallView).
 * Displays live BAC in 44sp Serif, status capsule, and a (+) Quick-Add button.
 */
class PromilleAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // No app process involved on reboot / widget re-add, so this only ever
        // sees the last snapshot the app wrote, never a live recompute.
        val snapshot = SharedStateStore.load(context)
        for (appWidgetId in appWidgetIds) {
            if (snapshot != null) {
                updateAppWidget(context, appWidgetManager, appWidgetId, snapshot.bac, snapshot.statusText, snapshot.statusColor)
            } else {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_QUICK_ADD = "EXTRA_OPEN_QUICK_ADD"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            bac: Double = 0.0,
            statusText: String = "Nüchtern",
            statusColor: Int = Color.parseColor("#6B9B6E")
        ) {
            val views = RemoteViews(context.packageName, R.layout.promille_widget).apply {
                val formattedBac = String.format(Locale.GERMANY, "%.2f", bac)
                setTextViewText(R.id.widget_bac_value, formattedBac)
                setTextColor(R.id.widget_bac_value, statusColor)
                setTextColor(R.id.widget_unit, statusColor)

                setTextViewText(R.id.widget_status, statusText)
                setTextColor(R.id.widget_status, statusColor)

                // Main App Launch PendingIntent
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val mainPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

                // Quick Add Action PendingIntent
                val quickAddIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_QUICK_ADD, true)
                }
                val quickAddPendingIntent = PendingIntent.getActivity(
                    context,
                    1,
                    quickAddIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_add_button, quickAddPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(
            context: Context,
            bac: Double,
            statusText: String,
            statusColor: Int
        ) {
            SharedStateStore.save(context, bac, statusText, statusColor)
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, PromilleAppWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName) ?: return
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id, bac, statusText, statusColor)
            }
        }
    }
}
