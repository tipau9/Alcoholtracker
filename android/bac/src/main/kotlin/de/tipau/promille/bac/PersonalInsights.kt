package de.tipau.promille.bac

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Mirrors Models/PersonalInsights.swift. iOS reads the clock, the calendar and
// WaterLog from globals; here they are parameters, which is what makes the
// discovery rules testable without a device.

enum class DayMood(val raw: Int, val emoji: String, val label: String) {
    NEUTRAL(0, "😐", "Kein Urteil"),
    HAPPY(1, "😄", "Guter Abend"),
    PROUD(2, "💪", "Gut gemacht"),
    REGRET(3, "😬", "Lieber nicht"),
    TERRIBLE(4, "🤢", "War zu viel");

    val isPositive: Boolean get() = this == HAPPY || this == PROUD
    val isNegative: Boolean get() = this == REGRET || this == TERRIBLE

    companion object {
        fun from(raw: Int): DayMood = entries.firstOrNull { it.raw == raw } ?: NEUTRAL
    }
}

/** [day] is the logical day the note belongs to, not a wall-clock timestamp. */
data class DayNote(
    val day: LocalDate,
    val text: String = "",
    val mood: DayMood = DayMood.NEUTRAL
)

data class BreathalyzerReading(
    val timestampEpochSeconds: Long,
    val measuredBAC: Double,
    val estimatedBAC: Double
)

data class RankedItem(val name: String, val subtitle: String, val count: Int) {
    val id: String get() = "$name|$subtitle"
}

data class TimeBucket(val value: Int, val count: Int) {
    val id: Int get() = value
}

data class Discovery(
    val title: String,
    val detail: String,
    val evidence: String,
    val icon: String
) {
    val id: String get() = "$title|$evidence"
}

data class PersonalInsights(
    val totalDrinks: Int,
    val drinkingDays: Int,
    val alcoholFreeDays: Int,
    val currentAlcoholFreeStreak: Int,
    val totalAlcoholGrams: Double,
    val totalCalories: Int,
    val totalVolumeML: Double,
    val averageDrinksPerDrinkingDay: Double,
    val averageDrinkMinutes: Double,
    val averageSessionMinutes: Double,
    val averageDrinksPerHour: Double,
    val averagePeakBAC: Double,
    val highestPeakBAC: Double,
    /** Logical day of the highest peak, null when there is nothing to rank. */
    val highestPeakDay: LocalDate?,
    val typicalStartMinutesAfterMidnight: Int?,
    val topDrinks: List<RankedItem>,
    val topCategories: List<RankedItem>,
    val hourly: List<TimeBucket>,
    val weekdays: List<TimeBucket>,
    val discoveries: List<Discovery>
) {
    companion object {
        val empty = PersonalInsights(
            totalDrinks = 0, drinkingDays = 0, alcoholFreeDays = 0,
            currentAlcoholFreeStreak = 0, totalAlcoholGrams = 0.0, totalCalories = 0,
            totalVolumeML = 0.0, averageDrinksPerDrinkingDay = 0.0,
            averageDrinkMinutes = 0.0, averageSessionMinutes = 0.0,
            averageDrinksPerHour = 0.0, averagePeakBAC = 0.0, highestPeakBAC = 0.0,
            highestPeakDay = null, typicalStartMinutesAfterMidnight = null,
            topDrinks = emptyList(), topCategories = emptyList(),
            hourly = emptyList(), weekdays = emptyList(), discoveries = emptyList()
        )

        private fun average(values: List<Double>): Double =
            if (values.isEmpty()) 0.0 else values.sum() / values.size

        fun build(
            drinks: List<Drink>,
            profile: Profile?,
            cutoffEpochSeconds: Long?,
            nowEpochSeconds: Long,
            notes: List<DayNote> = emptyList(),
            breathalyzerReadings: List<BreathalyzerReading> = emptyList(),
            waterLog: WaterLog = WaterLog.disabled(),
            pace: DrinkPaceMemory = DrinkPaceMemory.disabled(),
            zone: ZoneId = ZoneId.systemDefault()
        ): PersonalInsights {
            val alcohol = drinks
                .filter {
                    it.abv > 0.01 &&
                        (cutoffEpochSeconds == null || it.timestampEpochSeconds >= cutoffEpochSeconds) &&
                        it.timestampEpochSeconds <= nowEpochSeconds
                }
                .sortedBy { it.timestampEpochSeconds }
            if (alcohol.isEmpty()) return empty

            val sessions: Map<LocalDate, List<Drink>> = alcohol
                .groupBy { LogicalDay.dateOf(it.timestampEpochSeconds, zone) }

            val sessionValues = sessions.values.map { it.sortedBy { d -> d.timestampEpochSeconds } }
            val sessionMinutes = sessionValues.map { session ->
                val first = session.firstOrNull() ?: return@map 0.0
                val end = session.maxOf { it.estimatedFinishedAtEpochSeconds(pace) }
                min(
                    1440.0,
                    max(
                        first.effectiveDrinkDurationMinutes(pace),
                        (end - first.timestampEpochSeconds) / 60.0
                    )
                )
            }
            val sessionPaces = sessionValues.zip(sessionMinutes) { session, minutes ->
                session.size / max(minutes / 60.0, 0.25)
            }

            val peaks: List<Pair<LocalDate, Double>> = sessions.map { (day, dayDrinks) ->
                if (profile == null) {
                    day to 0.0
                } else {
                    day to BacProjectionInput(
                        drinks = dayDrinks,
                        profile = profile,
                        stomachStatus = profile.defaultStomachStatus,
                        conservative = profile.conservativeForApp,
                        pace = pace
                    ).peakBac()
                }
            }
            val highest = peaks.maxByOrNull { it.second }

            // The subtitle sticks to the category of the first sighting, exactly as
            // the iOS tuple does: renaming a drink's category later must not split
            // one name into two rows.
            val drinkCounts = LinkedHashMap<String, Pair<String, Int>>()
            val categoryCounts = LinkedHashMap<String, Int>()
            val hourCounts = HashMap<Int, Int>()
            val weekdayCounts = HashMap<Int, Int>()
            for (drink in alcohol) {
                val key = drink.name.trim()
                val old = drinkCounts[key] ?: (drink.category.germanName to 0)
                drinkCounts[key] = old.first to (old.second + 1)
                categoryCounts[drink.category.germanName] =
                    (categoryCounts[drink.category.germanName] ?: 0) + 1
                val local = java.time.Instant.ofEpochSecond(drink.timestampEpochSeconds).atZone(zone)
                hourCounts[local.hour] = (hourCounts[local.hour] ?: 0) + 1
                // Monday = 0 ... Sunday = 6.
                val weekday = local.dayOfWeek.value - 1
                weekdayCounts[weekday] = (weekdayCounts[weekday] ?: 0) + 1
            }

            val today = LogicalDay.dateOf(nowEpochSeconds, zone)
            val firstDay = cutoffEpochSeconds?.let { LogicalDay.dateOf(it, zone) }
                ?: sessions.keys.minOrNull()
                ?: today
            val observedDays = max(1L, ChronoUnit.DAYS.between(firstDay, today) + 1).toInt()
            val drinkingDaySet = sessions.keys
            val alcoholFreeDays = max(0, observedDays - drinkingDaySet.size)

            var currentStreak = 0
            var cursor = today
            while (cursor >= firstDay && !drinkingDaySet.contains(cursor)) {
                currentStreak += 1
                cursor = cursor.minusDays(1)
            }

            // Average session start on a circular 24-hour clock. Shift early-morning
            // starts past midnight so late-night sessions do not average to noon.
            val shiftedStarts = sessionValues.mapNotNull { session ->
                val first = session.firstOrNull() ?: return@mapNotNull null
                val local = java.time.Instant.ofEpochSecond(first.timestampEpochSeconds).atZone(zone)
                val raw = local.hour * 60 + local.minute
                if (raw < 6 * 60) raw + 24 * 60 else raw
            }
            val typicalStart = if (shiftedStarts.isEmpty()) {
                null
            } else {
                (shiftedStarts.sum().toDouble() / shiftedStarts.size).toInt() % (24 * 60)
            }

            val totalAlcoholGrams = alcohol.sumOf { it.alcoholGrams }
            val totalCalories = alcohol.sumOf { it.calories }
            val totalVolumeML = alcohol.sumOf { it.volumeML }
            val averagePerDrinkingDay = alcohol.size.toDouble() / max(sessions.size, 1)
            val drinkMinutes = average(alcohol.map { it.effectiveDrinkDurationMinutes(pace) })
            val peakAverage = average(peaks.map { it.second })

            fun topFive(items: List<RankedItem>): List<RankedItem> = items
                .sortedWith(compareByDescending<RankedItem> { it.count }.thenBy { it.name })
                .take(5)

            val topDrinks = topFive(
                drinkCounts.map { (name, value) ->
                    RankedItem(name = name, subtitle = value.first, count = value.second)
                }
            )
            val topCategories = topFive(
                categoryCounts.map { (name, count) ->
                    RankedItem(name = name, subtitle = "Kategorie", count = count)
                }
            )
            val hourly = (0 until 24).map { TimeBucket(it, hourCounts[it] ?: 0) }
            val weekdays = (0 until 7).map { TimeBucket(it, weekdayCounts[it] ?: 0) }

            val discoveries = mutableListOf<Discovery>()
            if (sessions.size >= 5) {
                val firstGaps = sessionValues.mapNotNull { session ->
                    if (session.size < 2) null
                    else (session[1].timestampEpochSeconds - session[0].timestampEpochSeconds) / 60.0
                }.sorted()
                if (firstGaps.size >= 5) {
                    val medianGap = firstGaps[firstGaps.size / 2]
                    if (medianGap < 30) {
                        discoveries.add(
                            Discovery(
                                title = "Schneller Einstieg",
                                detail = "Zwischen deinem ersten und zweiten Drink liegen typischerweise nur ${medianGap.roundToInt()} Minuten.",
                                evidence = "Median aus ${firstGaps.size} vergleichbaren Sessions",
                                icon = "bolt.fill"
                            )
                        )
                    }
                }

                val orderedSessions = sessions.entries.sortedBy { it.key }
                if (orderedSessions.size >= 8) {
                    fun shiftedEndMinute(session: List<Drink>): Double {
                        val end = session.maxOfOrNull { it.estimatedFinishedAtEpochSeconds(pace) }
                            ?: return 0.0
                        val local = java.time.Instant.ofEpochSecond(end).atZone(zone)
                        val raw = (local.hour * 60 + local.minute).toDouble()
                        return if (raw < 360) raw + 1440 else raw
                    }
                    val endMinutes = orderedSessions.map { shiftedEndMinute(it.value) }
                    val split = endMinutes.size / 2
                    val older = average(endMinutes.subList(0, split))
                    val newer = average(endMinutes.subList(split, endMinutes.size))
                    val shift = newer - older
                    if (abs(shift) >= 30) {
                        discoveries.add(
                            Discovery(
                                title = if (shift > 0) "Deine Abende enden später" else "Deine Abende enden früher",
                                detail = "Im jüngeren Zeitraum lag dein letzter Drink im Schnitt ${abs(shift).roundToInt()} Minuten ${if (shift > 0) "später" else "früher"}.",
                                evidence = "Vergleich von je $split Sessions",
                                icon = if (shift > 0) "moon.stars.fill" else "sunrise.fill"
                            )
                        )
                    }
                }

                val favorite = topDrinks.firstOrNull()
                if (favorite != null && alcohol.size >= 10) {
                    val share = favorite.count.toDouble() / alcohol.size
                    if (share >= 0.4) {
                        discoveries.add(
                            Discovery(
                                title = "Dein klarer Favorit",
                                detail = "${favorite.name} macht ${(share * 100).roundToInt()} % deiner Einträge in diesem Zeitraum aus.",
                                evidence = "${favorite.count} von ${alcohol.size} Drinks",
                                icon = "star.fill"
                            )
                        )
                    }
                }
            }

            val relevantReadings = breathalyzerReadings.filter {
                it.timestampEpochSeconds <= nowEpochSeconds &&
                    (cutoffEpochSeconds == null || it.timestampEpochSeconds >= cutoffEpochSeconds)
            }
            if (relevantReadings.size >= 5) {
                val differences = relevantReadings.map { it.measuredBAC - it.estimatedBAC }
                val bias = average(differences)
                val meanAbsoluteError = average(differences.map { abs(it) })
                discoveries.add(
                    Discovery(
                        title = "Messung und Schätzung",
                        detail = "Deine Breathalyser-Werte lagen im Mittel ${abs(bias).permilleString()} ${if (bias >= 0) "über" else "unter"} der App-Schätzung.",
                        evidence = "Ø absolute Abweichung ${meanAbsoluteError.permilleString()} aus ${relevantReadings.size} Messungen",
                        icon = "wind"
                    )
                )
            }

            if (notes.size >= 6) {
                val positive = notes.filter { it.mood.isPositive }
                    .mapNotNull { sessions[it.day]?.size }
                val negative = notes.filter { it.mood.isNegative }
                    .mapNotNull { sessions[it.day]?.size }
                if (positive.size >= 3 && negative.size >= 3) {
                    val positiveAverage = average(positive.map { it.toDouble() })
                    val negativeAverage = average(negative.map { it.toDouble() })
                    if (negativeAverage - positiveAverage >= 1) {
                        discoveries.add(
                            Discovery(
                                title = "Morgenstimmung und Drinkzahl",
                                detail = "Abende mit negativer Morgenbewertung hatten im Mittel ${(negativeAverage - positiveAverage).oneDecimalGerman()} mehr Drinks.",
                                evidence = "${positive.size + negative.size} bewertete Abende · Zusammenhang, keine Ursache",
                                icon = "face.dashed.fill"
                            )
                        )
                    }
                }

                val hydratedMoods = mutableListOf<Boolean>()
                val lessHydratedMoods = mutableListOf<Boolean>()
                for (note in notes) {
                    if (note.mood == DayMood.NEUTRAL) continue
                    val session = sessions[note.day]
                    if (session.isNullOrEmpty()) continue
                    // No entry means the day was never logged, which is not the same
                    // as zero glasses, so an unlogged day joins neither bucket.
                    val glasses = waterLog.loggedGlasses(note.day) ?: continue
                    if (glasses * 2 >= session.size) {
                        hydratedMoods.add(note.mood.isNegative)
                    } else {
                        lessHydratedMoods.add(note.mood.isNegative)
                    }
                }
                if (hydratedMoods.size >= 3 && lessHydratedMoods.size >= 3) {
                    val hydratedNegativeRate =
                        hydratedMoods.count { it }.toDouble() / hydratedMoods.size
                    val lessHydratedNegativeRate =
                        lessHydratedMoods.count { it }.toDouble() / lessHydratedMoods.size
                    if (lessHydratedNegativeRate - hydratedNegativeRate >= 0.2) {
                        discoveries.add(
                            Discovery(
                                title = "Wasser und Morgenstimmung",
                                detail = "An Abenden mit mindestens einem Glas Wasser je zwei Drinks hast du den Morgen seltener negativ bewertet.",
                                evidence = "${hydratedMoods.size + lessHydratedMoods.size} bewertete Abende · Zusammenhang, keine Ursache",
                                icon = "drop.fill"
                            )
                        )
                    }
                }
            }

            return PersonalInsights(
                totalDrinks = alcohol.size,
                drinkingDays = sessions.size,
                alcoholFreeDays = alcoholFreeDays,
                currentAlcoholFreeStreak = currentStreak,
                totalAlcoholGrams = totalAlcoholGrams,
                totalCalories = totalCalories,
                totalVolumeML = totalVolumeML,
                averageDrinksPerDrinkingDay = averagePerDrinkingDay,
                averageDrinkMinutes = drinkMinutes,
                averageSessionMinutes = average(sessionMinutes),
                averageDrinksPerHour = average(sessionPaces),
                averagePeakBAC = peakAverage,
                highestPeakBAC = highest?.second ?: 0.0,
                highestPeakDay = highest?.first,
                typicalStartMinutesAfterMidnight = typicalStart,
                topDrinks = topDrinks,
                topCategories = topCategories,
                hourly = hourly,
                weekdays = weekdays,
                discoveries = discoveries.take(5)
            )
        }
    }
}
