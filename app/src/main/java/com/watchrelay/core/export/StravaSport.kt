package com.watchrelay.core.export

import com.watchrelay.core.model.Sport

object StravaSport {
    fun activityType(sport: Sport): String = when (sport) {
        Sport.RUN -> "Run"
        Sport.CYCLE -> "Ride"
        Sport.WALK -> "Walk"
        Sport.HIKE -> "Hike"
        Sport.SWIM -> "Swim"
        Sport.STRENGTH -> "WeightTraining"
        Sport.YOGA -> "Yoga"
        Sport.HIIT -> "HighIntensityIntervalTraining"
        Sport.ROW -> "Rowing"
        Sport.SKI -> "AlpineSki"
        Sport.SKATE -> "IceSkate"
        Sport.ELLIPTICAL -> "Elliptical"
        Sport.OTHER -> "Workout"
    }

    fun gpxType(sport: Sport): String = activityType(sport)
}
