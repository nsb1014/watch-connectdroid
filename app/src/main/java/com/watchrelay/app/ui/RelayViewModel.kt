package com.watchrelay.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchrelay.app.WatchRelayApp
import com.watchrelay.app.data.StoredWorkout
import com.watchrelay.app.wifi.LanAddress
import com.watchrelay.app.wifi.ReceiveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Destination {
    data object Home : Destination()
    data object Receive : Destination()
    data object Library : Destination()
    data class Workout(val id: String) : Destination()
    data object Import : Destination()
    data object Export : Destination()
    data object Settings : Destination()
}

data class UiState(
    val screen: Destination = Destination.Home,
    val workouts: List<StoredWorkout> = emptyList(),
    val listening: Boolean = false,
    val listenUrl: String = "",
    val lastEvent: String = "Idle",
    val wifiName: String = "Wi‑Fi",
    val message: String? = null,
    val stravaConnected: Boolean = false,
    val stravaAthlete: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val selectedIds: Set<String> = emptySet(),
    val busy: Boolean = false
)

class RelayViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as WatchRelayApp).container
    private val screen = MutableStateFlow<Destination>(Destination.Home)
    private val message = MutableStateFlow<String?>(null)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val busy = MutableStateFlow(false)
    private val wifiName = MutableStateFlow(LanAddress.ssid(application))
    private val authTick = MutableStateFlow(0)

    val state: StateFlow<UiState> = combine(
        combine(screen, container.repository.observeWorkouts(), selected) { dest, workouts, ids ->
            Triple(dest, workouts, ids)
        },
        combine(ReceiveService.listening, ReceiveService.listenUrl, ReceiveService.lastEvent) { on, url, event ->
            Triple(on, url, event)
        },
        combine(message, busy, wifiName, authTick) { msg, isBusy, wifi, _ -> Triple(msg, isBusy, wifi) }
    ) { first, receive, extras ->
        UiState(
            screen = first.first,
            workouts = first.second,
            selectedIds = first.third,
            listening = receive.first,
            listenUrl = receive.second,
            lastEvent = receive.third,
            message = extras.first,
            busy = extras.second,
            wifiName = extras.third,
            stravaConnected = container.preferences.isStravaConnected,
            stravaAthlete = container.preferences.stravaAthlete,
            clientId = container.preferences.stravaClientId,
            clientSecret = container.preferences.stravaClientSecret
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun go(destination: Destination) {
        screen.value = destination
        message.value = null
    }

    fun back() {
        screen.value = when (screen.value) {
            is Destination.Workout -> Destination.Library
            Destination.Home -> Destination.Home
            else -> Destination.Home
        }
    }

    fun flash(text: String) {
        message.value = text
    }

    fun toggleListen() {
        val context = getApplication<Application>()
        if (ReceiveService.listening.value) {
            ReceiveService.stop(context)
        } else {
            ReceiveService.start(context)
            wifiName.value = LanAddress.ssid(context)
            ReceiveService.listenUrl.value = LanAddress.listenUrl()
            go(Destination.Receive)
        }
    }

    fun toggleSelected(id: String) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    fun saveStravaApp(clientId: String, clientSecret: String) {
        container.preferences.stravaClientId = clientId
        container.preferences.stravaClientSecret = clientSecret
        authTick.value += 1
        flash("Saved Strava app credentials on this phone.")
    }

    fun disconnectStrava() {
        container.preferences.clearStravaTokens()
        authTick.value += 1
        flash("Strava tokens cleared.")
    }

    fun authorizeUrl(): String = container.strava.authorizeUrl()

    fun handleStravaCode(code: String) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                withContext(Dispatchers.IO) { container.strava.exchangeCode(code) }
            }.onSuccess {
                authTick.value += 1
                flash("Strava connected${container.preferences.stravaAthlete.takeIf { it.isNotBlank() }?.let { " as $it" } ?: ""}.")
            }.onFailure { error ->
                flash(error.message ?: "Strava login failed")
            }
            busy.value = false
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            busy.value = true
            var count = 0
            uris.forEach { uri ->
                val name = queryName(uri) ?: uri.lastPathSegment ?: "import.bin"
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@forEach
                val imported = withContext(Dispatchers.IO) { container.repository.importBytes(name, bytes) }
                count += imported.size
            }
            flash(if (count == 0) "No workouts found in those files." else "Imported $count workout${if (count == 1) "" else "s"}.")
            if (count > 0) go(Destination.Library)
            busy.value = false
        }
    }

    fun loadSamples() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            busy.value = true
            val names = listOf("run.gpx", "bike.tcx", "export.xml", "workouts.json", "workouts.csv")
            var count = 0
            names.forEach { name ->
                val bytes = withContext(Dispatchers.IO) {
                    context.assets.open("samples/$name").use { it.readBytes() }
                }
                count += withContext(Dispatchers.IO) { container.repository.importBytes(name, bytes) }.size
            }
            flash("Loaded $count sample workouts.")
            go(Destination.Library)
            busy.value = false
        }
    }

    fun exportSelected() {
        val ids = selected.value
        if (ids.isEmpty()) {
            flash("Select at least one workout.")
            return
        }
        if (!container.preferences.hasStravaApp) {
            flash("Add your Strava client id and secret in Settings.")
            go(Destination.Settings)
            return
        }
        if (!container.preferences.isStravaConnected) {
            flash("Connect Strava, then export.")
            return
        }
        viewModelScope.launch {
            busy.value = true
            var ok = 0
            ids.forEach { id ->
                val stored = container.repository.get(id) ?: return@forEach
                runCatching {
                    withContext(Dispatchers.IO) { container.strava.upload(stored.workout) }
                }.onSuccess { uploadId ->
                    container.repository.markStrava(id, uploadId, "Uploaded · $uploadId")
                    ok += 1
                }.onFailure { error ->
                    container.repository.markStrava(id, null, error.message ?: "Upload failed")
                }
            }
            flash("Sent $ok of ${ids.size} workouts to Strava.")
            busy.value = false
        }
    }

    fun clearData() {
        viewModelScope.launch {
            container.repository.clear()
            selected.value = emptySet()
            flash("Local workouts cleared.")
        }
    }

    private fun queryName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
    }
}
