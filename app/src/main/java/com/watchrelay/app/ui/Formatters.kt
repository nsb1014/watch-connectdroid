package com.watchrelay.app.ui

import com.watchrelay.core.importing.SportMapper
import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.Sport
import com.watchrelay.core.model.Workout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val dateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())
    private val date: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    fun sport(sport: Sport): String = SportMapper.displayName(sport)

    fun formatLabel(format: FitnessFormat): String = when (format) {
        FitnessFormat.GPX -> "GPX"
        FitnessFormat.TCX -> "TCX"
        FitnessFormat.FIT -> "FIT"
        FitnessFormat.HEALTH_XML -> "Health XML"
        FitnessFormat.HEALTH_ZIP -> "Health ZIP"
        FitnessFormat.JSON -> "JSON"
        FitnessFormat.CSV -> "CSV"
        FitnessFormat.UNKNOWN -> "File"
    }

    fun whenStarted(millis: Long): String {
        if (millis <= 0) return "Unknown time"
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTime)
    }

    fun day(millis: Long): String {
        if (millis <= 0) return "—"
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(date)
    }

    fun duration(millis: Long): String {
        val totalSec = (millis / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun distance(meters: Double?): String {
        if (meters == null || meters <= 0) return "—"
        return if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000.0)
        else String.format(Locale.US, "%.0f m", meters)
    }

    fun calories(kcal: Double?): String =
        if (kcal == null || kcal <= 0) "—" else String.format(Locale.US, "%.0f kcal", kcal)

    fun hr(bpm: Int?): String = if (bpm == null || bpm <= 0) "—" else "$bpm bpm"

    fun summaryLine(workout: Workout): String =
        listOfNotNull(
            duration(workout.durationMillis),
            distance(workout.distanceMeters).takeIf { it != "—" },
            formatLabel(workout.sourceFormat)
        ).joinToString("  ·  ")
}
