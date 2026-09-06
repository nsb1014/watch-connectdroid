package com.watchrelay.core.importing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object TimeParse {
    private val appleHealth: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.US)
    private val compact: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val dateOnly: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun millisOrNull(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        value.toLongOrNull()?.let { numeric ->
            return if (numeric < 100_000_000_000L) numeric * 1000L else numeric
        }
        return tryParseInstant(value)
    }

    fun millisOrZero(raw: String?): Long = millisOrNull(raw) ?: 0L

    private fun tryParseInstant(value: String): Long? {
        val normalized = value.replace(' ', 'T').let { candidate ->
            if (candidate.endsWith("Z") || candidate.contains('+') || candidate.matches(Regex(".*[+-]\\d{2}:?\\d{2}$"))) {
                candidate
            } else {
                candidate
            }
        }
        try {
            return Instant.parse(if (value.endsWith("Z") || value.contains('T')) {
                if (value.contains(' ') && !value.contains('T')) value.replace(' ', 'T') else value
            } else normalized).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return ZonedDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return OffsetDateTime.parse(value, appleHealth).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDateTime.parse(value, compact).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            val withT = value.replace(' ', 'T')
            return LocalDateTime.parse(withT).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDate.parse(value, dateOnly).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        return null
    }
}
