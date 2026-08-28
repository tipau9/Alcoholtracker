package de.tipau.promille.bac

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// The app day starts at 06:00, not midnight: drinks logged between 00:00 and
// 05:59 belong to the previous evening. Single source of truth for session,
// history, achievements, safety and hydration. Mirrors CalendarLogicalDay.swift.
object LogicalDay {

    const val START_HOUR: Int = 6

    /** The calendar date that owns this timestamp. 01:30 on June 11 returns June 10. */
    fun dateOf(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val local = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return if (local.hour < START_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** 06:00 of the logical day that owns this timestamp, as epoch seconds. */
    fun startOf(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateOf(epochSeconds, zone).atTime(LocalTime.of(START_HOUR, 0)).atZone(zone).toEpochSecond()

    fun sameLogicalDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        dateOf(a, zone) == dateOf(b, zone)
}
