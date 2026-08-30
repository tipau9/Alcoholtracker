package de.tipau.promille.service

import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.data.DrinkDao
import de.tipau.promille.data.DrinkTemplateDao
import kotlin.math.abs

/**
 * Normalizes and validates stored data in Room database on startup.
 * Mirrors Alcoholtracker/Services/CompatibilityCheckService.swift 1:1.
 */
object CompatibilityCheckService {

    suspend fun normalizeStoredData(drinkDao: DrinkDao, templateDao: DrinkTemplateDao) {
        val templates = templateDao.getAll()
        for (template in templates) {
            var changed = false
            var cat = template.categoryRaw
            if (DrinkCategory.entries.none { it.raw == cat }) {
                cat = DrinkCategory.OTHER.raw
                changed = true
            }
            val safeABV = BarcodeService.sanitizedABV(template.abv)
            if (abs(template.abv - safeABV) > 0.001) {
                changed = true
            }
            val safeVolume = BarcodeService.sanitizedVolumeML(template.volume)
            if (abs(template.volume - safeVolume) > 0.001) {
                changed = true
            }
            if (changed) {
                templateDao.update(
                    template.copy(
                        categoryRaw = cat,
                        abv = safeABV,
                        volume = safeVolume
                    )
                )
            }
        }

        val drinks = drinkDao.getAllDrinksSortedOnce()
        for (drink in drinks) {
            var changed = false
            var cat = drink.categoryRaw
            if (DrinkCategory.entries.none { it.raw == cat }) {
                cat = DrinkCategory.OTHER.raw
                changed = true
            }
            var dur = drink.drinkDurationMinutes
            if (dur < 0 || !dur.isFinite()) {
                dur = 0.0
                changed = true
            }
            val safeABV = BarcodeService.sanitizedABV(drink.abv)
            if (abs(drink.abv - safeABV) > 0.001) {
                changed = true
            }
            val safeVolume = BarcodeService.sanitizedVolumeML(drink.volume)
            if (abs(drink.volume - safeVolume) > 0.001) {
                changed = true
            }
            if (changed) {
                drinkDao.update(
                    drink.copy(
                        categoryRaw = cat,
                        drinkDurationMinutes = dur,
                        abv = safeABV,
                        volume = safeVolume
                    )
                )
            }
        }
    }
}
