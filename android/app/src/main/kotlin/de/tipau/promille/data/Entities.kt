package de.tipau.promille.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room mirror of PersistenceController.schema. Conventions that hold for every
 * entity here, because they are what keeps the two apps comparable:
 *
 * - ids are lowercase UUID strings. Postgres stores `uuid`, and PostgREST hands
 *   it back lowercase, so normalising here means an offline-queue dedup compares
 *   equal strings on both platforms.
 * - timestamps are epoch SECONDS, matching the :bac timeline. Millis in one
 *   layer and seconds in the other is a factor 1000 inside a permille number.
 * - new columns get an inline default and no migration plan, the same
 *   lightweight-migration style the SwiftData models use.
 */

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    /* Singleton row, exactly like the single SwiftData UserProfile object. */
    @PrimaryKey val id: Int = 1,
    val weight: Double,
    val height: Double,
    val age: Int,
    val eliminationRate: Double,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val toleranceMode: Boolean = false,
    val warningThreshold: Double = 0.5,
    val tipsyThreshold: Double = 0.01,
    val drunkThreshold: Double = 0.30,
    val carefulThreshold: Double = 0.80,
    val dangerThreshold: Double = 1.50,
    val birthDate: Long = 0,
    val accentColorHex: String = "C9802F",
    val sipVolumeML: Double = 25.0,
    val genderRaw: String = "diverse",
    val homeStyleRaw: String = "detailed",
    val activeWidgetsRaw: String = "",
    val homeSectionOrderRaw: String = "",
    val stomachStatusRaw: String = "light",
    val statusSkinRaw: String = "standard",
    val onboardingStepsCompletedRaw: String = "",
    val activeMedicationsRaw: String = "",
    val healthKitEnabled: Boolean = false,
    val weeklyDrinkLimit: Int = 0,
    val soberDaysGoal: Int = 4,
    val isProbationaryDriver: Boolean = false,
    val drunkModeAuto: Boolean = false,
    val conservativeSafety: Boolean = false,
    val conservativeEverywhere: Boolean = false
)

@Entity(
    tableName = "drink_template",
    indices = [Index("name"), Index("categoryRaw")]
)
data class DrinkTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryRaw: String,
    val volume: Double,
    val abv: Double,
    val calories: Int,
    val iconName: String,
    val isCustom: Boolean = false,
    val usageCount: Int = 0,
    val barcode: String = ""
)

@Entity(
    tableName = "drink",
    indices = [Index("timestamp")]
)
data class DrinkEntity(
    @PrimaryKey val id: String,
    val templateID: String? = null,
    val name: String,
    val volume: Double,
    val abv: Double,
    val calories: Int,
    val iconName: String,
    @ColumnInfo(name = "timestamp") val timestampEpochSeconds: Long,
    val categoryRaw: String,
    val mixerVolume: Double = 0.0,
    val mixerWaterContent: Double = 0.0,
    val drinkDurationMinutes: Double = 0.0
)

@Entity(tableName = "custom_mix")
data class CustomMixEntity(
    @PrimaryKey val id: String,
    val name: String,
    /* iOS keeps this as Data holding JSON [MixIngredient]; same JSON, as text. */
    val ingredientsJson: String,
    val createdAt: Long
)

@Entity(tableName = "crew_member")
data class CrewMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarInitial: String,
    val currentBAC: Double = 0.0,
    val lastDrinkTimestamp: Long? = null,
    val drinksLastHour: Int = 0,
    val isHome: Boolean = false,
    val isSoberBuddy: Boolean = false,
    val isSharing: Boolean = false,
    val isSelf: Boolean = false,
    val joinedAt: Long,
    val friendCode: String? = null,
    val sosActive: Boolean = false,
    val isProbationaryDriver: Boolean = false,
    val alertWhenHigh: Boolean = false,
    val highAlertFired: Boolean = false
)

@Entity(tableName = "photo_memory")
data class PhotoMemoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val filename: String,
    val caption: String? = null,
    val bacAtTime: Double? = null
)

/**
 * Keyed on the logical day as "yyyy-MM-dd", which is both what iOS derives
 * (Calendar.logicalDay returns the midnight of the owning 06:00 day) and what
 * day_notes carries on the wire, so no format conversion sits in between.
 */
@Entity(tableName = "day_note")
data class DayNoteEntity(
    @PrimaryKey val day: String,
    val text: String = "",
    val moodRaw: Int = 0
)

@Entity(tableName = "pending_sync_operation")
data class PendingSyncOperationEntity(
    @PrimaryKey val id: String,
    val operationType: String,
    val payload: String,
    val createdAt: Long,
    val retryCount: Int = 0
)

@Entity(tableName = "vomit_event")
data class VomitEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long
)

@Entity(tableName = "meal_event")
data class MealEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val impactRaw: String,
    val name: String = ""
)

@Entity(tableName = "breathalyzer_reading")
data class BreathalyzerReadingEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val measuredBAC: Double,
    val estimatedBAC: Double,
    val sourceRaw: String,
    val note: String = ""
)
