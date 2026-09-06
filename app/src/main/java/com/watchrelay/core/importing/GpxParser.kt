package com.watchrelay.core.importing

import com.watchrelay.core.importing.XmlSupport.attr
import com.watchrelay.core.importing.XmlSupport.children
import com.watchrelay.core.importing.XmlSupport.descendants
import com.watchrelay.core.importing.XmlSupport.localOrName
import com.watchrelay.core.importing.XmlSupport.textOf
import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import org.w3c.dom.Element

object GpxParser {
    fun parse(fileName: String, bytes: ByteArray): List<Workout> {
        val document = XmlSupport.parse(bytes)
        val root = document.documentElement ?: return emptyList()
        val preview = WorkoutFactory.preview(bytes)
        val tracks = root.descendants("trk")
        if (tracks.isEmpty()) {
            val points = root.descendants("trkpt").ifEmpty { root.descendants("rtept") }
            if (points.isEmpty()) return emptyList()
            return listOf(workoutFromPoints(fileName, "GPX workout", points, preview))
        }
        return tracks.map { track ->
            val name = track.textOf("name") ?: root.textOf("name") ?: "GPX workout"
            val points = track.descendants("trkpt")
            workoutFromPoints(fileName, name, points, preview)
        }
    }

    private fun workoutFromPoints(
        fileName: String,
        name: String,
        points: List<Element>,
        preview: String
    ): Workout {
        val track = points.map { element ->
            val hr = firstNumeric(element, "hr", "heartrate")
            val cad = firstNumeric(element, "cad", "cadence")
            TrackPoint(
                timeMillis = TimeParse.millisOrNull(element.textOf("time")),
                latitude = element.attr("lat")?.toDoubleOrNull(),
                longitude = element.attr("lon")?.toDoubleOrNull(),
                elevationMeters = element.textOf("ele")?.toDoubleOrNull(),
                heartRateBpm = hr?.toInt(),
                cadenceSpm = cad?.toInt()
            )
        }
        val sport = SportMapper.fromLabel(name)
        return WorkoutFactory.build(
            name = name,
            sport = sport,
            startMillis = 0L,
            endMillis = 0L,
            durationMillis = 0L,
            distanceMeters = null,
            caloriesKcal = null,
            sourceFormat = FitnessFormat.GPX,
            sourceFileName = fileName,
            track = track,
            rawPreview = preview
        )
    }

    private fun firstNumeric(element: Element, vararg localNames: String): Double? {
        val wanted = localNames.map { it.lowercase() }.toSet()
        val stack = ArrayDeque<Element>()
        stack.add(element)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current !== element && current.localOrName() in wanted) {
                current.textContent.trim().toDoubleOrNull()?.let { return it }
            }
            current.children(current.localOrName())
            val kids = current.childNodes
            for (i in 0 until kids.length) {
                val node = kids.item(i)
                if (node is Element) stack.add(node)
            }
        }
        return null
    }
}
