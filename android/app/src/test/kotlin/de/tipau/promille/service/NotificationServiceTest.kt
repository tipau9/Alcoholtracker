package de.tipau.promille.service

import de.tipau.promille.bac.BacProjectionInput
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.Gender
import de.tipau.promille.bac.Profile
import de.tipau.promille.bac.StomachStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationServiceTest {

    private val profile = Profile(
        weightKg = 75.0,
        heightCm = 180.0,
        age = 25,
        gender = Gender.MALE,
        warningThreshold = 0.5,
        tipsyThreshold = 0.01,
        conservativeSafety = true
    )

    @Test
    fun `no notifications planned when drinks are empty`() {
        val input = BacProjectionInput(
            drinks = emptyList(),
            profile = profile,
            stomachStatus = StomachStatus.EMPTY,
            conservative = true
        )
        val plans = NotificationService.planNotifications(
            input = input,
            tipsyThreshold = profile.tipsyThreshold,
            warningThreshold = profile.warningThreshold,
            nowEpochSeconds = 1_000_000L
        )
        assertTrue(plans.isEmpty(), "empty drinks must produce no scheduled notifications")
    }

    @Test
    fun `plans both sober and drive notifications for high BAC session`() {
        val now = 1_000_000L
        val drink = Drink(
            id = "d1",
            name = "Beer 1L",
            volumeML = 1000.0,
            abv = 6.0,
            calories = 500,
            category = DrinkCategory.BEER,
            timestampEpochSeconds = now
        )
        val input = BacProjectionInput(
            drinks = listOf(drink),
            profile = profile,
            stomachStatus = StomachStatus.EMPTY,
            conservative = true
        )

        val plans = NotificationService.planNotifications(
            input = input,
            tipsyThreshold = profile.tipsyThreshold,
            warningThreshold = profile.warningThreshold,
            nowEpochSeconds = now
        )

        assertEquals(2, plans.size, "should schedule both sober and warning threshold notifications")

        val soberPlan = plans.first { it.id == NotificationService.SOBER_NOTIFICATION_ID }
        assertEquals("Wieder nüchtern", soberPlan.title)
        assertTrue(soberPlan.body.contains("0,01 ‰"), "must format decimal comma properly: ${soberPlan.body}")
        assertFalse(soberPlan.body.contains("—"), "no em-dashes allowed")
        assertTrue(soberPlan.delaySeconds >= 60L, "minimum delay must be at least 60s")

        val drivePlan = plans.first { it.id == NotificationService.DRIVE_NOTIFICATION_ID }
        assertEquals("Unter deiner Warnschwelle", drivePlan.title)
        assertTrue(drivePlan.body.contains("0,50 ‰"), "must format decimal comma properly: ${drivePlan.body}")
        assertFalse(drivePlan.body.contains("—"), "no em-dashes allowed")
        assertTrue(drivePlan.delaySeconds >= 60L, "minimum delay must be at least 60s")
        assertTrue(drivePlan.delaySeconds < soberPlan.delaySeconds, "drive threshold is reached before completely sober")
    }

    @Test
    fun `does not schedule if time until threshold is less than 0_05 hours`() {
        val now = 1_000_000L
        // Very small sip of low ABV drink
        val sip = Drink(
            id = "d1",
            name = "Tiny Sip",
            volumeML = 10.0,
            abv = 2.0,
            calories = 5,
            category = DrinkCategory.BEER,
            timestampEpochSeconds = now - 3600 // already completely metabolized
        )
        val input = BacProjectionInput(
            drinks = listOf(sip),
            profile = profile,
            stomachStatus = StomachStatus.FULL,
            conservative = true
        )

        val plans = NotificationService.planNotifications(
            input = input,
            tipsyThreshold = profile.tipsyThreshold,
            warningThreshold = profile.warningThreshold,
            nowEpochSeconds = now
        )

        assertTrue(plans.isEmpty(), "zero or near-zero BAC must not schedule notifications")
    }
}
