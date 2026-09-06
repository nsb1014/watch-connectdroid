package com.watchrelay.core.importing

import com.watchrelay.core.model.Sport

object SportMapper {
    fun fromLabel(raw: String?): Sport {
        val value = raw.orEmpty().lowercase()
        return when {
            value.contains("run") || value.contains("jog") -> Sport.RUN
            value.contains("cycl") || value.contains("bike") || value.contains("bik") || value.contains("ride") -> Sport.CYCLE
            value.contains("walk") -> Sport.WALK
            value.contains("hike") || value.contains("trail") -> Sport.HIKE
            value.contains("swim") -> Sport.SWIM
            value.contains("strength") || value.contains("weight") || value.contains("functionalstrength") -> Sport.STRENGTH
            value.contains("yoga") -> Sport.YOGA
            value.contains("hiit") || value.contains("highintensity") || value.contains("functional") -> Sport.HIIT
            value.contains("row") -> Sport.ROW
            value.contains("ski") || value.contains("snowboard") -> Sport.SKI
            value.contains("skate") -> Sport.SKATE
            value.contains("elliptical") -> Sport.ELLIPTICAL
            else -> Sport.OTHER
        }
    }

    fun fromFit(sport: Int): Sport = when (sport) {
        1 -> Sport.RUN
        2 -> Sport.CYCLE
        5 -> Sport.SWIM
        10 -> Sport.WALK
        11 -> Sport.HIKE
        15 -> Sport.ROW
        13 -> Sport.SKI
        17 -> Sport.SKATE
        20, 22 -> Sport.STRENGTH
        25 -> Sport.YOGA
        else -> Sport.OTHER
    }

    fun displayName(sport: Sport): String = when (sport) {
        Sport.RUN -> "Run"
        Sport.CYCLE -> "Ride"
        Sport.WALK -> "Walk"
        Sport.HIKE -> "Hike"
        Sport.SWIM -> "Swim"
        Sport.STRENGTH -> "Strength"
        Sport.YOGA -> "Yoga"
        Sport.HIIT -> "HIIT"
        Sport.ROW -> "Row"
        Sport.SKI -> "Ski"
        Sport.SKATE -> "Skate"
        Sport.ELLIPTICAL -> "Elliptical"
        Sport.OTHER -> "Workout"
    }
}
