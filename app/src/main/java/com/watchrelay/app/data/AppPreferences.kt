package com.watchrelay.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context)

    var stravaClientId: String
        get() = prefs.getString(KEY_CLIENT_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value.trim()).apply()

    var stravaClientSecret: String
        get() = prefs.getString(KEY_CLIENT_SECRET, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CLIENT_SECRET, value.trim()).apply()

    var stravaAccessToken: String
        get() = prefs.getString(KEY_ACCESS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    var stravaRefreshToken: String
        get() = prefs.getString(KEY_REFRESH, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var stravaAthlete: String
        get() = prefs.getString(KEY_ATHLETE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ATHLETE, value).apply()

    val hasStravaApp: Boolean
        get() = stravaClientId.isNotBlank() && stravaClientSecret.isNotBlank()

    val isStravaConnected: Boolean
        get() = stravaAccessToken.isNotBlank()

    fun clearStravaTokens() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_ATHLETE)
            .apply()
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val master = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "watchrelay.secure",
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences("watchrelay.prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_CLIENT_ID = "strava_client_id"
        private const val KEY_CLIENT_SECRET = "strava_client_secret"
        private const val KEY_ACCESS = "strava_access"
        private const val KEY_REFRESH = "strava_refresh"
        private const val KEY_ATHLETE = "strava_athlete"
    }
}
