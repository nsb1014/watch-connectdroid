package com.watchrelay.core.importing

import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.HeartSample
import com.watchrelay.core.model.Split
import com.watchrelay.core.model.Sport
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object WorkoutFactory {
    fun build(
        name: String,
        sport: Sport,
        startMillis: Long,
        endMillis: Long,
        durationMillis: Long,
        distanceMeters: Double?,
        caloriesKcal: Double?,
        sourceFormat: FitnessFormat,
        sourceFileName: String,
        notes: String? = null,
        track: List<TrackPoint> = emptyList(),
        heartSamples: List<HeartSample> = emptyList(),
        splits: List<Split> = emptyList(),
        rawPreview: String = ""
    ): Workout {
        val resolvedStart = when {
            startMillis > 0 -> startMillis
            track.firstOrNull()?.timeMillis != null -> track.first().timeMillis!!
            heartSamples.isNotEmpty() -> heartSamples.first().timeMillis
            else -> 0L
        }
        val resolvedEnd = when {
            endMillis > 0 -> endMillis
            track.lastOrNull()?.timeMillis != null -> track.last().timeMillis!!
            heartSamples.isNotEmpty() -> heartSamples.last().timeMillis
            durationMillis > 0 && resolvedStart > 0 -> resolvedStart + durationMillis
            else -> resolvedStart
        }
        val resolvedDuration = when {
            durationMillis > 0 -> durationMillis
            resolvedEnd > resolvedStart -> resolvedEnd - resolvedStart
            else -> 0L
        }
        val hrs = if (heartSamples.isNotEmpty()) {
            heartSamples
        } else {
            track.mapNotNull { point ->
                val time = point.timeMillis ?: return@mapNotNull null
                val hr = point.heartRateBpm ?: return@mapNotNull null
                HeartSample(time, hr)
            }
        }
        val avgHr = hrs.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt()
        val maxHr = hrs.maxOfOrNull { it.bpm }
        val distance = distanceMeters
            ?: track.mapNotNull { it.distanceMeters }.maxOrNull()
            ?: distanceFromTrack(track)
        val id = stableId(sourceFormat, sourceFileName, name, resolvedStart, resolvedDuration)
        return Workout(
            id = id,
            name = name.ifBlank { SportMapper.displayName(sport) },
            sport = sport,
            startMillis = resolvedStart,
            endMillis = resolvedEnd,
            durationMillis = resolvedDuration,
            distanceMeters = distance,
            caloriesKcal = caloriesKcal,
            avgHeartRateBpm = avgHr,
            maxHeartRateBpm = maxHr,
            sourceFormat = sourceFormat,
            sourceFileName = sourceFileName,
            notes = notes,
            track = track,
            heartSamples = hrs,
            splits = splits,
            rawPreview = rawPreview
        )
    }

    fun preview(bytes: ByteArray, limit: Int = 4000): String {
        val count = minOf(bytes.size, limit)
        return String(bytes, 0, count, Charsets.UTF_8)
    }

    fun previewBinaryLabel(fileName: String, bytes: ByteArray): String =
        "Binary ${fileName.ifBlank { "file" }} (${bytes.size} bytes)"

    private fun distanceFromTrack(track: List<TrackPoint>): Double? {
        val coords = track.filter { it.latitude != null && it.longitude != null }
        if (coords.size < 2) return null
        var meters = 0.0
        for (i in 1 until coords.size) {
            meters += haversine(
                coords[i - 1].latitude!!,
                coords[i - 1].longitude!!,
                coords[i].latitude!!,
                coords[i].longitude!!
            )
        }
        return meters
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    private fun stableId(
        format: FitnessFormat,
        fileName: String,
        name: String,
        start: Long,
        duration: Long
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = "$format|$fileName|$name|$start|$duration".lowercase(Locale.US)
        val hash = digest.digest(key.toByteArray())
        val hex = hash.take(16).joinToString("") { byte -> "%02x".format(byte) }
        return UUID.nameUUIDFromBytes(hex.toByteArray()).toString()
    }
}
