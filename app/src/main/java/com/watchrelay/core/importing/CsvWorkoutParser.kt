package com.watchrelay.core.importing

import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.Workout

object CsvWorkoutParser {
    fun parse(fileName: String, bytes: ByteArray): List<Workout> {
        val text = String(bytes, Charsets.UTF_8).trim().trimStart('\uFEFF')
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val headers = splitRow(lines.first()).map { normalizeHeader(it) }
        val preview = WorkoutFactory.preview(bytes)
        return lines.drop(1).mapNotNull { line ->
            val cols = splitRow(line)
            if (cols.isEmpty()) return@mapNotNull null
            fun col(vararg names: String): String? {
                for (name in names) {
                    val index = headers.indexOf(name)
                    if (index >= 0 && index < cols.size && cols[index].isNotBlank()) return cols[index]
                }
                return null
            }
            val sportLabel = col("type", "sport", "activity", "name") ?: "Workout"
            val durationRaw = col("duration_min", "durationmin", "minutes", "duration")?.toDoubleOrNull()
            val durationMillis = when {
                headers.contains("duration_min") || headers.contains("durationmin") || headers.contains("minutes") ->
                    ((durationRaw ?: 0.0) * 60_000).toLong()
                headers.contains("duration_sec") || headers.contains("durationsec") ->
                    ((col("duration_sec", "durationsec")?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                else -> ((durationRaw ?: 0.0) * 60_000).toLong()
            }
            val distanceKm = col("distance_km", "distancekm", "km")?.toDoubleOrNull()
            val distanceM = col("distance_m", "distancem", "distance")?.toDoubleOrNull()
            val distance = when {
                distanceKm != null -> distanceKm * 1000.0
                distanceM != null && distanceM < 200 -> distanceM * 1000.0
                else -> distanceM
            }
            WorkoutFactory.build(
                name = sportLabel,
                sport = SportMapper.fromLabel(sportLabel),
                startMillis = TimeParse.millisOrZero(col("date", "start", "start_time", "timestamp")),
                endMillis = 0L,
                durationMillis = durationMillis,
                distanceMeters = distance,
                caloriesKcal = col("calories", "kcal")?.toDoubleOrNull(),
                sourceFormat = FitnessFormat.CSV,
                sourceFileName = fileName,
                notes = col("notes", "comment"),
                heartSamples = emptyList(),
                rawPreview = preview
            ).let { workout ->
                val avg = col("avg_hr", "avghr", "heartrate", "hr")?.toDoubleOrNull()?.toInt()
                if (avg == null) workout else workout.copy(avgHeartRateBpm = avg)
            }
        }
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().lowercase().replace(" ", "_").replace("-", "_")

    private fun splitRow(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        quoted = !quoted
                    }
                }
                ch == ',' && !quoted -> {
                    out += current.toString().trim()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        out += current.toString().trim()
        return out
    }
}
