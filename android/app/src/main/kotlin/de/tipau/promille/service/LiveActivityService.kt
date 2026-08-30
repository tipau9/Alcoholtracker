package de.tipau.promille.service

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Android counterpart to iOS LiveActivityService.swift / ActivityKit.
 * Manages the ongoing status notification on Android.
 */
class LiveActivityService private constructor() {

    companion object {
        val shared = LiveActivityService()
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())

    fun syncActivity(
        context: Context,
        bac: Double,
        eliminationRate: Double,
        drinkCount: Int,
        soberThreshold: Double = 0.01,
        warningThreshold: Double = 0.5,
        drivingLimit: Double = 0.5,
        soberAt: Date? = null,
        driveReadyAt: Date? = null
    ) {
        if (bac <= soberThreshold) {
            NotificationService.cancelLiveNotification(context)
            return
        }

        val statusText = when {
            bac >= 1.5 -> "Gefahrenbereich"
            bac >= 0.8 -> "Betrunken"
            bac >= 0.5 -> "Angeschlagen"
            bac >= 0.2 -> "Leicht angeheitert"
            else -> "Nuechtern werden"
        }

        val soberTimeStr = soberAt?.let { timeFormatter.format(it.toInstant()) }

        NotificationService.updateLiveNotification(
            context = context,
            bac = bac,
            statusText = statusText,
            trendSymbol = if (bac > 0.1) "\u2198" else "\u2192",
            soberTimeStr = soberTimeStr
        )
    }
}

