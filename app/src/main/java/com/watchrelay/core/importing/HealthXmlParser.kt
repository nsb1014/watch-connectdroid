package com.watchrelay.core.importing

import com.watchrelay.core.importing.XmlSupport.attr
import com.watchrelay.core.importing.XmlSupport.descendants
import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.HeartSample
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import org.w3c.dom.Element

object HealthXmlParser {
    fun parse(fileName: String, bytes: ByteArray): List<Workout> {
        val root = XmlSupport.parse(bytes).documentElement ?: return emptyList()
        val preview = WorkoutFactory.preview(bytes)
        val workouts = root.descendants("workout")
        val heart = root.descendants("record").mapNotNull { record ->
            val type = record.attr("type").orEmpty()
            if (!type.contains("HeartRate") || type.contains("Variability")) return@mapNotNull null
            val time = TimeParse.millisOrNull(record.attr("startDate")) ?: return@mapNotNull null
            val bpm = record.attr("value")?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            HeartSample(time, bpm)
        }.sortedBy { it.timeMillis }
        val routes = root.descendants("workoutroute").associate { route ->
            val start = TimeParse.millisOrZero(route.attr("startDate"))
            start to route.descendants("trackpoint").ifEmpty { route.descendants("trkpt") }
        }
        if (workouts.isEmpty()) return emptyList()
        return workouts.map { node -> toWorkout(fileName, node, heart, routes, preview) }
    }

    private fun toWorkout(
        fileName: String,
        node: Element,
        heart: List<HeartSample>,
        routes: Map<Long, List<Element>>,
        preview: String
    ): Workout {
        val activity = node.attr("workoutActivityType") ?: node.attr("type") ?: "Workout"
        val start = TimeParse.millisOrZero(node.attr("startDate"))
        val end = TimeParse.millisOrZero(node.attr("endDate"))
        val duration = durationMillis(node.attr("duration"), node.attr("durationUnit"))
        val distance = distanceMeters(node.attr("totalDistance"), node.attr("totalDistanceUnit"))
        val calories = node.attr("totalEnergyBurned")?.toDoubleOrNull()
        val nearbyHeart = heart.filter { sample ->
            val lo = if (start > 0) start - 5_000 else Long.MIN_VALUE
            val hi = if (end > 0) end + 5_000 else Long.MAX_VALUE
            sample.timeMillis in lo..hi
        }
        val routeNodes = routes.entries
            .filter { (routeStart, _) -> start == 0L || kotlin.math.abs(routeStart - start) < 120_000 }
            .flatMap { it.value }
        val track = routeNodes.map { point ->
            TrackPoint(
                timeMillis = TimeParse.millisOrNull(point.attr("time") ?: point.attr("startDate")),
                latitude = (point.attr("lat") ?: point.attr("latitude"))?.toDoubleOrNull(),
                longitude = (point.attr("lon") ?: point.attr("longitude"))?.toDoubleOrNull(),
                elevationMeters = point.attr("ele")?.toDoubleOrNull()
            )
        }
        return WorkoutFactory.build(
            name = prettyHealthName(activity),
            sport = SportMapper.fromLabel(activity),
            startMillis = start,
            endMillis = end,
            durationMillis = duration,
            distanceMeters = distance,
            caloriesKcal = calories,
            sourceFormat = FitnessFormat.HEALTH_XML,
            sourceFileName = fileName,
            track = track,
            heartSamples = nearbyHeart,
            rawPreview = preview
        )
    }

    private fun durationMillis(raw: String?, unit: String?): Long {
        val value = raw?.toDoubleOrNull() ?: return 0L
        return when (unit?.lowercase()) {
            "s", "sec", "secs", "second", "seconds" -> (value * 1000).toLong()
            "h", "hr", "hour", "hours" -> (value * 3_600_000).toLong()
            else -> (value * 60_000).toLong()
        }
    }

    private fun distanceMeters(raw: String?, unit: String?): Double? {
        val value = raw?.toDoubleOrNull() ?: return null
        return when (unit?.lowercase()) {
            "m", "meter", "meters" -> value
            "mi", "mile", "miles" -> value * 1609.344
            "ft", "feet" -> value * 0.3048
            else -> value * 1000.0
        }
    }

    private fun prettyHealthName(type: String): String {
        val trimmed = type.substringAfter("HKWorkoutActivityType")
        return if (trimmed.isBlank() || trimmed == type) type else trimmed.replace(Regex("([a-z])([A-Z])"), "$1 $2")
    }
}
