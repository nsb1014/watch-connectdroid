package com.watchrelay.core.export

import com.watchrelay.core.model.Workout
import java.time.Instant

object GpxWriter {
    fun write(workout: Workout): String {
        val name = escape(workout.name)
        val points = workout.track.filter { it.latitude != null && it.longitude != null }
        val body = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="WatchRelay" xmlns="http://www.topografix.com/GPX/1/1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">"""
            )
            appendLine("  <metadata>")
            appendLine("    <name>$name</name>")
            if (workout.startMillis > 0) {
                appendLine("    <time>${Instant.ofEpochMilli(workout.startMillis)}</time>")
            }
            appendLine("  </metadata>")
            appendLine("  <trk>")
            appendLine("    <name>$name</name>")
            appendLine("    <type>${StravaSport.gpxType(workout.sport)}</type>")
            appendLine("    <trkseg>")
            if (points.isEmpty()) {
                val start = if (workout.startMillis > 0) Instant.ofEpochMilli(workout.startMillis) else Instant.EPOCH
                appendLine("""      <trkpt lat="0.0" lon="0.0"><time>$start</time></trkpt>""")
            } else {
                for (point in points) {
                    append("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
                    if (point.elevationMeters != null) append("<ele>${point.elevationMeters}</ele>")
                    if (point.timeMillis != null) append("<time>${Instant.ofEpochMilli(point.timeMillis)}</time>")
                    if (point.heartRateBpm != null) {
                        append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>${point.heartRateBpm}</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
                    }
                    appendLine("</trkpt>")
                }
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
        return body
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
