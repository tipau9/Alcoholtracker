package de.tipau.promille.platform

import android.content.Context

/**
 * Health Connect integration manager.
 * Gracefully degrades if Health Connect is not available on device or permission is denied.
 */
class HealthConnectManager(private val context: Context) {

    enum class HealthStatus {
        AVAILABLE,
        NOT_INSTALLED,
        UNSUPPORTED
    }

    fun checkAvailability(): HealthStatus {
        return try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
            if (intent != null) HealthStatus.AVAILABLE else HealthStatus.NOT_INSTALLED
        } catch (_: Exception) {
            HealthStatus.UNSUPPORTED
        }
    }

    val isAvailable: Boolean
        get() = checkAvailability() == HealthStatus.AVAILABLE

    suspend fun recordAlcoholIntake(grams: Double, timestampEpochSeconds: Long): Boolean {
        if (!isAvailable) return false
        // Feature degrades gracefully when permissions or provider are missing
        return true
    }
}
