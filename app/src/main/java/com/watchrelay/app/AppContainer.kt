package com.watchrelay.app

import android.content.Context
import com.watchrelay.app.data.AppPreferences
import com.watchrelay.app.data.WatchRelayDatabase
import com.watchrelay.app.data.WorkoutRepository
import com.watchrelay.app.strava.StravaClient

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val database = WatchRelayDatabase.create(app)
    val repository = WorkoutRepository(app, database.workouts())
    val preferences = AppPreferences(app)
    val strava = StravaClient(preferences)
}
