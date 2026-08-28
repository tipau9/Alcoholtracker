package de.tipau.promille.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import de.tipau.promille.R

/**
 * Homescreen widget mirroring the iOS WidgetKit complication:
 * Displays estimated current BAC and status.
 */
class PromilleAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            bac: Double = 0.0,
            statusText: String = context.getString(R.string.status_sober)
        ) {
            val views = RemoteViews(context.packageName, R.layout.promille_widget).apply {
                val formattedBac = String.format(java.util.Locale.GERMAN, "%.2f ‰", bac)
                setTextViewText(R.id.widget_bac_value, formattedBac)
                setTextViewText(R.id.widget_status, statusText)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
