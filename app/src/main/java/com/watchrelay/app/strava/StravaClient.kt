package com.watchrelay.app.strava

import com.watchrelay.app.data.AppPreferences
import com.watchrelay.core.export.GpxWriter
import com.watchrelay.core.export.StravaSport
import com.watchrelay.core.model.Workout
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StravaClient(
    private val prefs: AppPreferences,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    fun authorizeUrl(): String {
        val clientId = prefs.stravaClientId
        return "https://www.strava.com/oauth/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=$REDIRECT" +
            "&response_type=code" +
            "&approval_prompt=auto" +
            "&scope=activity:write,read"
    }

    fun exchangeCode(code: String) {
        val body = FormBody.Builder()
            .add("client_id", prefs.stravaClientId)
            .add("client_secret", prefs.stravaClientSecret)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .build()
        val json = postForm("https://www.strava.com/oauth/token", body)
        storeTokens(json)
    }

    fun upload(workout: Workout): String {
        ensureFreshToken()
        val gpx = GpxWriter.write(workout)
        val fileBody = gpx.toByteArray().toRequestBody("application/gpx+xml".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "${workout.id}.gpx", fileBody)
            .addFormDataPart("data_type", "gpx")
            .addFormDataPart("name", workout.name)
            .addFormDataPart("activity_type", StravaSport.activityType(workout.sport))
            .addFormDataPart("description", "Imported with WatchRelay from ${workout.sourceFormat} (${workout.sourceFileName})")
            .build()
        val request = Request.Builder()
            .url("https://www.strava.com/api/v3/uploads")
            .header("Authorization", "Bearer ${prefs.stravaAccessToken}")
            .post(multipart)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Strava upload failed (${response.code}): $text")
            }
            val id = JSONObject(text).opt("id")?.toString() ?: "queued"
            return id
        }
    }

    private fun ensureFreshToken() {
        if (prefs.stravaAccessToken.isBlank()) {
            throw IllegalStateException("Connect Strava in Settings first.")
        }
        if (prefs.stravaRefreshToken.isBlank()) return
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", prefs.stravaClientId)
                .add("client_secret", prefs.stravaClientSecret)
                .add("grant_type", "refresh_token")
                .add("refresh_token", prefs.stravaRefreshToken)
                .build()
            storeTokens(postForm("https://www.strava.com/oauth/token", body))
        }
    }

    private fun postForm(url: String, body: FormBody): JSONObject {
        val request = Request.Builder().url(url).post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Strava auth failed (${response.code}): $text")
            }
            return JSONObject(text)
        }
    }

    private fun storeTokens(json: JSONObject) {
        prefs.stravaAccessToken = json.optString("access_token")
        prefs.stravaRefreshToken = json.optString("refresh_token")
        val athlete = json.optJSONObject("athlete")
        prefs.stravaAthlete = athlete?.optString("username").orEmpty()
            .ifBlank { athlete?.optString("firstname").orEmpty() }
    }

    companion object {
        const val REDIRECT = "watchrelay://strava/callback"
    }
}
