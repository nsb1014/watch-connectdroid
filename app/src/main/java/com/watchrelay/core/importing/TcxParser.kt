package com.watchrelay.core.importing

import com.watchrelay.core.importing.XmlSupport.descendants
import com.watchrelay.core.importing.XmlSupport.textOf
import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.Split
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import org.w3c.dom.Element

object TcxParser {
    fun parse(fileName: String, bytes: ByteArray): List<Workout> {
        val root = XmlSupport.parse(bytes).documentElement ?: return emptyList()
        val preview = WorkoutFactory.preview(bytes)
        val activities = root.descendants("activity")
        if (activities.isEmpty()) return emptyList()
        return activities.map { activity -> workout(fileName, activity, preview) }
    }

    private fun workout(fileName: String, activity: Element, preview: String): Workout {
        val sportLabel = activity.getAttribute("Sport").ifBlank { activity.getAttribute("sport") }
        val laps = activity.descendants("lap")
        val points = mutableListOf<TrackPoint>()
        val splits = mutableListOf<Split>()
        var totalDistance = 0.0
        var totalCalories = 0.0
        var totalSeconds = 0.0
        laps.forEachIndexed { index, lap ->
            val seconds = lap.textOf("totaltimeseconds")?.toDoubleOrNull() ?: 0.0
            val distance = lap.textOf("distancemeters")?.toDoubleOrNull()
            val calories = lap.textOf("calories")?.toDoubleOrNull()
            val avgHr = firstValue(lap, "averageheartratebpm")?.toInt()
            totalSeconds += seconds
            if (distance != null) totalDistance += distance
            if (calories != null) totalCalories += calories
            splits += Split(
                index = index + 1,
                durationMillis = (seconds * 1000).toLong(),
                distanceMeters = distance,
                avgHeartRateBpm = avgHr
            )
            lap.descendants("trackpoint").forEach { point ->
                points += TrackPoint(
                    timeMillis = TimeParse.millisOrNull(point.textOf("time")),
                    latitude = firstText(point, "latitudedegrees")?.toDoubleOrNull(),
                    longitude = firstText(point, "longitudedegrees")?.toDoubleOrNull(),
                    elevationMeters = point.textOf("altitudemeters")?.toDoubleOrNull(),
                    heartRateBpm = firstValue(point, "heartratebpm")?.toInt(),
                    cadenceSpm = point.textOf("cadence")?.toDoubleOrNull()?.toInt(),
                    distanceMeters = point.textOf("distancemeters")?.toDoubleOrNull()
                )
            }
        }
        val start = TimeParse.millisOrZero(activity.textOf("id"))
            .takeIf { it > 0 }
            ?: TimeParse.millisOrZero(laps.firstOrNull()?.getAttribute("StartTime"))
        return WorkoutFactory.build(
            name = sportLabel.ifBlank { "TCX workout" },
            sport = SportMapper.fromLabel(sportLabel),
            startMillis = start,
            endMillis = 0L,
            durationMillis = (totalSeconds * 1000).toLong(),
            distanceMeters = totalDistance.takeIf { it > 0 },
            caloriesKcal = totalCalories.takeIf { it > 0 },
            sourceFormat = FitnessFormat.TCX,
            sourceFileName = fileName,
            track = points,
            splits = splits,
            rawPreview = preview
        )
    }

    private fun firstValue(element: Element, wrapperTag: String): Double? {
        val wrapper = element.descendants(wrapperTag).firstOrNull() ?: return null
        return wrapper.textOf("value")?.toDoubleOrNull() ?: wrapper.textContent.trim().toDoubleOrNull()
    }

    private fun firstText(element: Element, tag: String): String? =
        element.descendants(tag).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
