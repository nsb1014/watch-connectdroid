package com.watchrelay.app.data

import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.HeartSample
import com.watchrelay.core.model.Split
import com.watchrelay.core.model.Sport
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import org.json.JSONArray
import org.json.JSONObject

object Codec {
    fun toEntity(workout: Workout, originalPath: String?): WorkoutEntity = WorkoutEntity(
        id = workout.id,
        name = workout.name,
        sport = workout.sport.name,
        startMillis = workout.startMillis,
        endMillis = workout.endMillis,
        durationMillis = workout.durationMillis,
        distanceMeters = workout.distanceMeters,
        caloriesKcal = workout.caloriesKcal,
        avgHeartRateBpm = workout.avgHeartRateBpm,
        maxHeartRateBpm = workout.maxHeartRateBpm,
        sourceFormat = workout.sourceFormat.name,
        sourceFileName = workout.sourceFileName,
        notes = workout.notes,
        rawPreview = workout.rawPreview,
        originalPath = originalPath,
        trackJson = trackJson(workout.track),
        samplesJson = samplesJson(workout.heartSamples),
        splitsJson = splitsJson(workout.splits),
        stravaUploadId = null,
        stravaStatus = null
    )

    fun toModel(entity: WorkoutEntity): Workout = Workout(
        id = entity.id,
        name = entity.name,
        sport = runCatching { Sport.valueOf(entity.sport) }.getOrDefault(Sport.OTHER),
        startMillis = entity.startMillis,
        endMillis = entity.endMillis,
        durationMillis = entity.durationMillis,
        distanceMeters = entity.distanceMeters,
        caloriesKcal = entity.caloriesKcal,
        avgHeartRateBpm = entity.avgHeartRateBpm,
        maxHeartRateBpm = entity.maxHeartRateBpm,
        sourceFormat = runCatching { FitnessFormat.valueOf(entity.sourceFormat) }.getOrDefault(FitnessFormat.UNKNOWN),
        sourceFileName = entity.sourceFileName,
        notes = entity.notes,
        track = parseTrack(entity.trackJson),
        heartSamples = parseSamples(entity.samplesJson),
        splits = parseSplits(entity.splitsJson),
        rawPreview = entity.rawPreview
    )

    private fun trackJson(points: List<TrackPoint>): String {
        val array = JSONArray()
        points.forEach { point ->
            array.put(
                JSONObject()
                    .put("t", point.timeMillis)
                    .put("lat", point.latitude)
                    .put("lon", point.longitude)
                    .put("ele", point.elevationMeters)
                    .put("hr", point.heartRateBpm)
                    .put("cad", point.cadenceSpm)
                    .put("dist", point.distanceMeters)
                    .put("spd", point.speedMps)
            )
        }
        return array.toString()
    }

    private fun samplesJson(samples: List<HeartSample>): String {
        val array = JSONArray()
        samples.forEach { sample ->
            array.put(JSONObject().put("t", sample.timeMillis).put("bpm", sample.bpm))
        }
        return array.toString()
    }

    private fun splitsJson(splits: List<Split>): String {
        val array = JSONArray()
        splits.forEach { split ->
            array.put(
                JSONObject()
                    .put("i", split.index)
                    .put("d", split.durationMillis)
                    .put("m", split.distanceMeters)
                    .put("hr", split.avgHeartRateBpm)
            )
        }
        return array.toString()
    }

    private fun parseTrack(raw: String): List<TrackPoint> {
        val array = JSONArray(raw.ifBlank { "[]" })
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            TrackPoint(
                timeMillis = obj.optLongOrNull("t"),
                latitude = obj.optDoubleOrNull("lat"),
                longitude = obj.optDoubleOrNull("lon"),
                elevationMeters = obj.optDoubleOrNull("ele"),
                heartRateBpm = obj.optIntOrNull("hr"),
                cadenceSpm = obj.optIntOrNull("cad"),
                distanceMeters = obj.optDoubleOrNull("dist"),
                speedMps = obj.optDoubleOrNull("spd")
            )
        }
    }

    private fun parseSamples(raw: String): List<HeartSample> {
        val array = JSONArray(raw.ifBlank { "[]" })
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            HeartSample(obj.getLong("t"), obj.getInt("bpm"))
        }
    }

    private fun parseSplits(raw: String): List<Split> {
        val array = JSONArray(raw.ifBlank { "[]" })
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            Split(
                index = obj.getInt("i"),
                durationMillis = obj.getLong("d"),
                distanceMeters = obj.optDoubleOrNull("m"),
                avgHeartRateBpm = obj.optIntOrNull("hr")
            )
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
}
