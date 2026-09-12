package de.tipau.promille.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import de.tipau.promille.PromilleApplication
import de.tipau.promille.bac.BacProjectionInput
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.color
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.UserProfileRepository
import de.tipau.promille.widget.PromilleAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Handles Lock Screen & Live Activity Quick Action buttons (+ Wasser, + 1 Bier)
 * directly without bringing the app into foreground.
 */
class QuickActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ADD_WATER = "de.tipau.promille.action.ADD_WATER"
        const val ACTION_ADD_BEER = "de.tipau.promille.action.ADD_BEER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PromilleApplication ?: return
        val container = app.container
        val now = System.currentTimeMillis() / 1000

        when (intent.action) {
            ACTION_ADD_WATER -> {
                container.waterLog.addGlassToday(now)
                CoroutineScope(Dispatchers.IO).launch {
                    refreshState(context, container, now)
                }
            }
            ACTION_ADD_BEER -> {
                val beer = DrinkEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Bier",
                    volume = 500.0,
                    abv = 5.0,
                    calories = 215,
                    iconName = "mug.fill",
                    timestampEpochSeconds = now,
                    categoryRaw = DrinkCategory.BEER.raw,
                    drinkDurationMinutes = 20.0
                )
                CoroutineScope(Dispatchers.IO).launch {
                    container.drinkRepository.addDrink(beer)
                    refreshState(context, container, now)
                }
            }
        }
    }

    private suspend fun refreshState(context: Context, container: de.tipau.promille.di.AppContainer, now: Long) {
        val entityProfile = container.userProfileRepository.getProfileOnce() ?: return
        val profile = UserProfileRepository.toProfile(entityProfile)
        val allDrinks = container.drinkRepository.getAllDrinksSortedOnce()
            .map { DrinkRepository.toDomainDrink(it) }

        val proj = BacProjectionInput(
            drinks = allDrinks,
            profile = profile,
            stomachStatus = profile.defaultStomachStatus,
            conservative = profile.conservativeForApp
        )

        val current = proj.currentBac(now)
        val status = BacStatus.of(current, profile)
        val past = proj.currentBac((now - 300).coerceAtLeast(0L))
        val trendSymbol = if (current >= past) "↗" else "↘"

        val soberHours = proj.hoursUntil(profile.tipsyThreshold, now)
        val soberTimeStr = soberHours?.let {
            val totalMin = (it * 60).toInt()
            val time = LocalTime.now().plusMinutes(totalMin.toLong())
            time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
        }

        NotificationService.updateLiveNotification(
            context = context,
            bac = current,
            statusText = status.germanName,
            trendSymbol = trendSymbol,
            soberTimeStr = soberTimeStr
        )

        val color = status.color.toArgb()
        PromilleAppWidgetProvider.updateAllWidgets(
            context = context,
            bac = current,
            statusText = status.germanName,
            statusColor = color
        )
    }
}
