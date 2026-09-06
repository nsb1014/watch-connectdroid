package com.watchrelay.core.importing

import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.HeartSample
import com.watchrelay.core.model.Split
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import org.json.JSONArray
import org.json.JSONObject

object JsonWorkoutParser {
    fun parse(fileName: String, bytes: ByteArray): List<Workout> {
        val text = String(bytes, Charsets.UTF_8).trim().trimStart('\uFEFF')
        val preview = WorkoutFactory.preview(bytes)
        val root: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
        val workouts = mutableListOf<JSONObject>()
        collectWorkouts(root, workouts)
        return workouts.map { node -> toWorkout(fileName, node, preview) }
    }

    private fun collectWorkouts(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) collectWorkouts(node.opt(i), out)
            }
            is JSONObject -> {
                if (looksLikeWorkout(node)) {
                    out += node
                    return
                }
                node.keys().forEach { key ->
                    val child = node.opt(key)
                    if (child is JSONArray || child is JSONObject) {
                        collectWorkouts(child, out)
                    }
                }
            }
        }
    }

    private fun looksLikeWorkout(node: JSONObject): Boolean {
        val keys = node.keys().asSequence().map { it.lowercase() }.toSet()
        val hasTime = keys.any { it.contains("start") || it.contains("date") || it == "id" }
        val hasBody = keys.any {
            it.contains("duration") || it.contains("distance") || it.contains("workout") ||
                it.contains("route") || it.contains("type") || it.contains("sport")
        }
        return hasTime && hasBody && !keys.contains("lat") && !keys.contains("latitude")
    }

    private fun toWorkout(fileName: String, node: JSONObject, preview: String): Workout {
        val name = firstString(node, "name", "title", "workoutName") ?: "JSON workout"
        val sport = SportMapper.fromLabel(
            firstString(node, "type", "sport", "activityType", "workoutActivityType", "name")
        )
        val start = TimeParse.millisOrZero(
            firstString(node, "start", "startDate", "start_time", "startedAt", "date")
        )
        val end = TimeParse.millisOrZero(firstString(node, "end", "endDate", "end_time", "endedAt"))
        val duration = firstNumber(node, "duration_sec", "durationSeconds", "duration")?.let { value ->
            val key = firstKey(node, "duration_sec", "durationSeconds", "duration")
            if (key == "duration" && value > 10_000) value.toLong() else (value * 1000).toLong()
        } ?: 0L
        val distance = firstNumber(node, "distance_m", "distanceMeters", "distance")?.let { value ->
            val key = firstKey(node, "distance_m", "distanceMeters", "distance")
            if (key == "distance" && value < 200) value * 1000.0 else value
        }
        val calories = firstNumber(node, "calories", "kcal", "activeEnergy")
        val route = asArray(node, "route", "points", "track", "locations")
        val track = route.map { item ->
            val obj = item as? JSONObject ?: return@map TrackPoint()
            TrackPoint(
                timeMillis = TimeParse.millisOrNull(firstString(obj, "time", "t", "timestamp", "date")),
                latitude = firstNumber(obj, "lat", "latitude"),
                longitude = firstNumber(obj, "lon", "lng", "longitude"),
                elevationMeters = firstNumber(obj, "ele", "elevation", "alt"),
                heartRateBpm = firstNumber(obj, "hr", "heartRate", "bpm")?.toInt()
            )
        }
        val samples = asArray(node, "heart_rate", "heartRate", "hr", "samples").mapNotNull { item ->
            val obj = item as? JSONObject ?: return@mapNotNull null
            val time = TimeParse.millisOrNull(firstString(obj, "t", "time", "timestamp", "date")) ?: return@mapNotNull null
            val bpm = firstNumber(obj, "bpm", "hr", "value")?.toInt() ?: return@mapNotNull null
            HeartSample(time, bpm)
        }
        val splits = asArray(node, "splits", "laps").mapIndexedNotNull { index, item ->
            val obj = item as? JSONObject ?: return@mapIndexedNotNull null
            Split(
                index = index + 1,
                durationMillis = (firstNumber(obj, "duration_sec", "duration") ?: 0.0).let { it * 1000 }.toLong(),
                distanceMeters = firstNumber(obj, "distance_m", "distance"),
                avgHeartRateBpm = firstNumber(obj, "avg_hr", "avgHeartRate")?.toInt()
            )
        }
        return WorkoutFactory.build(
            name = name,
            sport = sport,
            startMillis = start,
            endMillis = end,
            durationMillis = duration,
            distanceMeters = distance,
            caloriesKcal = calories,
            sourceFormat = FitnessFormat.JSON,
            sourceFileName = fileName,
            track = track,
            heartSamples = samples,
            splits = splits,
            rawPreview = preview
        )
    }

    private fun firstString(node: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (node.has(key) && !node.isNull(key)) {
                val value = node.opt(key)?.toString()?.trim()
                if (!value.isNullOrEmpty() && value != "null") return value
            }
        }
        return null
    }

    private fun firstNumber(node: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!node.has(key) || node.isNull(key)) continue
            val value = node.opt(key)
            when (value) {
                is Number -> return value.toDouble()
                is String -> value.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun firstKey(node: JSONObject, vararg keys: String): String? =
        keys.firstOrNull { node.has(it) && !node.isNull(it) }

    private fun asArray(node: JSONObject, vararg keys: String): List<Any> {
        for (key in keys) {
            val value = node.opt(key)
            if (value is JSONArray) {
                return (0 until value.length()).map { value.opt(it) }
            }
        }
        return emptyList()
    }
}
