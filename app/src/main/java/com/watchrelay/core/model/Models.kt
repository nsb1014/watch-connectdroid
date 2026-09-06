package com.watchrelay.core.model

enum class Sport {
    RUN, CYCLE, WALK, HIKE, SWIM, STRENGTH, YOGA, HIIT, ROW, SKI, SKATE, ELLIPTICAL, OTHER
}

enum class FitnessFormat {
    GPX, TCX, FIT, HEALTH_XML, HEALTH_ZIP, JSON, CSV, UNKNOWN
}

data class TrackPoint(
    val timeMillis: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevationMeters: Double? = null,
    val heartRateBpm: Int? = null,
    val cadenceSpm: Int? = null,
    val distanceMeters: Double? = null,
    val speedMps: Double? = null
)

data class HeartSample(
    val timeMillis: Long,
    val bpm: Int
)

data class Split(
    val index: Int,
    val durationMillis: Long,
    val distanceMeters: Double? = null,
    val avgHeartRateBpm: Int? = null
)

data class Workout(
    val id: String,
    val name: String,
    val sport: Sport,
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double? = null,
    val caloriesKcal: Double? = null,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val sourceFormat: FitnessFormat,
    val sourceFileName: String,
    val notes: String? = null,
    val track: List<TrackPoint> = emptyList(),
    val heartSamples: List<HeartSample> = emptyList(),
    val splits: List<Split> = emptyList(),
    val rawPreview: String = ""
)

fun Workout.derivedHeartSamples(): List<HeartSample> {
    if (heartSamples.isNotEmpty()) return heartSamples
    return track.mapNotNull { point ->
        val time = point.timeMillis ?: return@mapNotNull null
        val hr = point.heartRateBpm ?: return@mapNotNull null
        HeartSample(time, hr)
    }
}
