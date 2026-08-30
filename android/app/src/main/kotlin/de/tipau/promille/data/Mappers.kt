package de.tipau.promille.data

import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.Gender
import de.tipau.promille.bac.MealEvent
import de.tipau.promille.bac.MealImpact
import de.tipau.promille.bac.Profile
import de.tipau.promille.bac.StomachStatus

/*
 * Room row <-> :bac domain type. Deliberately free of Android imports so it runs
 * in a plain JVM unit test: this is the layer where a seconds/millis or a raw
 * value typo would land in a permille number without anything else noticing.
 */

/** Lowercase, because Postgres hands `uuid` columns back lowercase. */
fun newId(): String = java.util.UUID.randomUUID().toString().lowercase()

fun normalizeId(id: String): String = id.trim().lowercase()

fun DrinkEntity.toDomain(): Drink = Drink(
    id = id,
    name = name,
    volumeML = volume,
    abv = abv,
    calories = calories,
    iconName = iconName,
    category = DrinkCategory.from(categoryRaw),
    timestampEpochSeconds = timestampEpochSeconds,
    templateId = templateID,
    mixerVolumeML = mixerVolume,
    mixerWaterContentPercent = mixerWaterContent,
    drinkDurationMinutes = drinkDurationMinutes
)

fun Drink.toEntity(): DrinkEntity = DrinkEntity(
    id = normalizeId(id),
    templateID = templateId?.let(::normalizeId),
    name = name,
    volume = volumeML,
    abv = abv,
    calories = calories,
    iconName = iconName,
    timestampEpochSeconds = timestampEpochSeconds,
    categoryRaw = category.raw,
    mixerVolume = mixerVolumeML,
    mixerWaterContent = mixerWaterContentPercent,
    drinkDurationMinutes = drinkDurationMinutes
)

fun MealEventEntity.toDomain(): MealEvent = MealEvent(
    id = id,
    timestampEpochSeconds = timestamp,
    impact = MealImpact.from(impactRaw),
    name = name
)

fun MealEvent.toEntity(): MealEventEntity = MealEventEntity(
    id = normalizeId(id),
    timestamp = timestampEpochSeconds,
    impactRaw = impact.raw,
    name = name
)

fun UserProfileEntity.toDomain(): Profile = Profile(
    weightKg = weight,
    heightCm = height,
    age = age,
    gender = Gender.from(genderRaw),
    eliminationRate = eliminationRate,
    toleranceMode = toleranceMode,
    isProbationaryDriver = isProbationaryDriver,
    conservativeSafety = conservativeSafety,
    conservativeEverywhere = conservativeEverywhere,
    defaultStomachStatus = StomachStatus.from(stomachStatusRaw),
    warningThreshold = warningThreshold,
    tipsyThreshold = tipsyThreshold,
    drunkThreshold = drunkThreshold,
    carefulThreshold = carefulThreshold,
    dangerThreshold = dangerThreshold
)

/**
 * Writes the domain fields back onto an existing row. The entity carries a lot
 * of UI-only state (accent colour, widget order, onboarding steps) that the
 * engine never sees, so a full rebuild from Profile would silently reset it.
 */
fun UserProfileEntity.applying(profile: Profile): UserProfileEntity = copy(
    weight = profile.weightKg,
    height = profile.heightCm,
    age = profile.age,
    genderRaw = profile.gender.raw,
    eliminationRate = profile.eliminationRate,
    toleranceMode = profile.toleranceMode,
    isProbationaryDriver = profile.isProbationaryDriver,
    conservativeSafety = profile.conservativeSafety,
    conservativeEverywhere = profile.conservativeEverywhere,
    stomachStatusRaw = profile.defaultStomachStatus.raw,
    warningThreshold = profile.warningThreshold,
    tipsyThreshold = profile.tipsyThreshold,
    drunkThreshold = profile.drunkThreshold,
    carefulThreshold = profile.carefulThreshold,
    dangerThreshold = profile.dangerThreshold
)

/** Matches the SwiftData UserProfile defaults, so a fresh install starts equal. */
fun defaultProfileEntity(nowEpochSeconds: Long): UserProfileEntity = UserProfileEntity(
    weight = 70.0,
    height = 175.0,
    age = 25,
    eliminationRate = 0.15,
    // iOS seeds birthDate as "now minus 25 years"; 25 * 365.25 days in seconds.
    birthDate = nowEpochSeconds - 788_940_000L
)
