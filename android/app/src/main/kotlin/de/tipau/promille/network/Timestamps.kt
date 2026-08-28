package de.tipau.promille.network

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * PostgREST hands timestamptz back in a few shapes: with fractional seconds,
 * without them, with a +00:00 offset or with none at all. The Swift side keeps
 * two ISO8601 formatters and tries both; this does the same, and treats a
 * missing offset as UTC because that is what Postgres stores.
 */
object Timestamps {

    private val withOffset: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun format(epochSeconds: Double): String =
        DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli((epochSeconds * 1000).toLong())
        )

    fun parse(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        runCatching { return OffsetDateTime.parse(trimmed, withOffset).toInstant().epochSecondsDouble() }
        runCatching { return Instant.parse(trimmed).epochSecondsDouble() }
        runCatching {
            return LocalDateTime.parse(trimmed.replace(" ", "T"))
                .toInstant(ZoneOffset.UTC).epochSecondsDouble()
        }
        return null
    }

    private fun Instant.epochSecondsDouble(): Double = epochSecond + nano / 1_000_000_000.0
}
