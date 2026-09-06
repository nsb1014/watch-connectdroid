package com.watchrelay.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchRelayAppUi(model: RelayViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> model.importUris(uris) }

    RelayTheme {
        Scaffold(
            containerColor = Ink,
            topBar = {
                TopAppBar(
                    title = { Text(barTitle(state.screen), color = TextMain) },
                    navigationIcon = {
                        if (state.screen !is Destination.Home) {
                            IconButton(onClick = { model.back() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextMain)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
                )
            }
        ) { padding ->
            val modifier = Modifier
                .fillMaxSize()
                .padding(padding)
            when (val dest = state.screen) {
                Destination.Home -> HomePane(state, model, modifier, permissionLauncher, fileLauncher)
                Destination.Receive -> ReceivePane(state, model, modifier)
                Destination.Library -> LibraryPane(state, model, modifier)
                is Destination.Workout -> WorkoutPane(state, dest.id, modifier)
                Destination.Import -> ImportPane(state, model, modifier, fileLauncher)
                Destination.Export -> ExportPane(state, model, modifier) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.authorizeUrl())))
                }
                Destination.Settings -> SettingsPane(state, model, modifier) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }
            }
        }
    }
}

private fun barTitle(destination: Destination): String = when (destination) {
    Destination.Home -> "WatchRelay"
    Destination.Receive -> "Wi‑Fi receive"
    Destination.Library -> "Library"
    is Destination.Workout -> "Workout"
    Destination.Import -> "Import"
    Destination.Export -> "Strava"
    Destination.Settings -> "Settings"
}

@Composable
private fun HomePane(
    state: UiState,
    model: RelayViewModel,
    modifier: Modifier,
    permissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    fileLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, List<android.net.Uri>>
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("WatchRelay", "Apple Watch fitness files → this phone → Strava.")
        Banner(state.message)
        RelayCard {
            Text(if (state.listening) "Listening on ${state.wifiName}" else "Wi‑Fi receive is off", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.listening) state.listenUrl else "Start the local receiver, then send an export from the iPhone/Watch on this network.",
                style = MaterialTheme.typography.bodyMedium
            )
            PrimaryButton(if (state.listening) "Open receiver" else "Start Wi‑Fi receive") {
                val needed = buildList {
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }.toTypedArray()
                if (needed.isNotEmpty()) permissionLauncher.launch(needed)
                if (state.listening) model.go(Destination.Receive) else model.toggleListen()
            }
        }
        RelayCard {
            Text("Library", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.workouts.isEmpty()) "No workouts on this phone yet."
                else "${state.workouts.size} stored locally · last ${state.workouts.first().workout.name}",
                style = MaterialTheme.typography.bodyMedium
            )
            PrimaryButton("View workouts") { model.go(Destination.Library) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("Import files", Modifier.weight(1f)) {
                fileLauncher.launch(arrayOf("*/*"))
                model.go(Destination.Import)
            }
            GhostButton("Strava", Modifier.weight(1f)) { model.go(Destination.Export) }
        }
        GhostButton("Settings") { model.go(Destination.Settings) }
    }
}

@Composable
private fun ReceivePane(state: UiState, model: RelayViewModel, modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(
            if (state.listening) "Listening" else "Receiver stopped",
            "Apple Watch will not pair with Android. Put the iPhone on ${state.wifiName} and send a Health/workout export to the address below."
        )
        Banner(state.lastEvent)
        RelayCard {
            Text("LAN address", style = MaterialTheme.typography.bodyMedium)
            Text(state.listenUrl, style = MaterialTheme.typography.titleLarge)
            Text("Open that URL in Safari on the iPhone, or POST a file to /import.", style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(if (state.listening) "Stop receiver" else "Start receiver") { model.toggleListen() }
        GhostButton("View library") { model.go(Destination.Library) }
    }
}

@Composable
private fun LibraryPane(state: UiState, model: RelayViewModel, modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle("Library", "${state.workouts.size} workouts on this phone")
        Banner(state.message)
        if (state.workouts.isEmpty()) {
            RelayCard {
                Text("Nothing imported yet.", style = MaterialTheme.typography.titleMedium)
                Text("Load the bundled samples, pick files, or receive over Wi‑Fi.", style = MaterialTheme.typography.bodyMedium)
                PrimaryButton("Load sample workouts") { model.loadSamples() }
            }
        } else {
            state.workouts.forEach { stored ->
                val workout = stored.workout
                RelayCard(onClick = { model.go(Destination.Workout(workout.id)) }) {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium)
                    Text(Formatters.whenStarted(workout.startMillis), style = MaterialTheme.typography.bodyMedium)
                    Text(Formatters.summaryLine(workout), style = MaterialTheme.typography.bodyLarge, color = TextMain)
                    if (!stored.stravaStatus.isNullOrBlank()) {
                        Text(stored.stravaStatus, color = Sky, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPane(
    state: UiState,
    model: RelayViewModel,
    modifier: Modifier,
    fileLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, List<android.net.Uri>>
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Import", "GPX · TCX · FIT · Health XML/ZIP · JSON · CSV")
        Banner(state.message)
        RelayCard {
            Text("From this phone", style = MaterialTheme.typography.titleMedium)
            Text("Use the system picker. Files stay in WatchRelay’s app storage.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(if (state.busy) "Importing…" else "Choose files", enabled = !state.busy) {
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
        RelayCard {
            Text("Try it without a watch", style = MaterialTheme.typography.titleMedium)
            Text("Loads the same sample exports the unit tests use.", style = MaterialTheme.typography.bodyMedium)
            GhostButton("Load samples") { model.loadSamples() }
        }
    }
}

@Composable
private fun ExportPane(state: UiState, model: RelayViewModel, modifier: Modifier, connect: () -> Unit) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle("Export to Strava", if (state.stravaConnected) "Connected${state.stravaAthlete.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}" else "Not connected")
        Banner(state.message)
        if (!state.stravaConnected) {
            PrimaryButton("Connect Strava") { connect() }
        }
        state.workouts.forEach { stored ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = stored.workout.id in state.selectedIds,
                    onCheckedChange = { model.toggleSelected(stored.workout.id) }
                )
                Column(Modifier.weight(1f)) {
                    Text(stored.workout.name, style = MaterialTheme.typography.titleMedium)
                    Text(Formatters.summaryLine(stored.workout), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        PrimaryButton(if (state.busy) "Uploading…" else "Upload selected", enabled = !state.busy) {
            model.exportSelected()
        }
    }
}

@Composable
private fun SettingsPane(state: UiState, model: RelayViewModel, modifier: Modifier, openAppSettings: () -> Unit) {
    var clientId by rememberSaveable { mutableStateOf(state.clientId) }
    var clientSecret by rememberSaveable { mutableStateOf(state.clientSecret) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Settings", "Local install only. Data never leaves the phone unless you upload to Strava.")
        Banner(state.message)
        RelayCard {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Text("Wi‑Fi / nearby devices, notifications for the receiver, and the system file picker for storage.", style = MaterialTheme.typography.bodyMedium)
            GhostButton("System permission settings") { openAppSettings() }
        }
        RelayCard {
            Text("Strava API app", style = MaterialTheme.typography.titleMedium)
            Text("Create an app at strava.com/settings/api. Callback: watchrelay://strava/callback", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Client ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = { Text("Client secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            PrimaryButton("Save credentials") { model.saveStravaApp(clientId, clientSecret) }
            if (state.stravaConnected) {
                GhostButton("Disconnect Strava") { model.disconnectStrava() }
            }
        }
        GhostButton("Clear local workouts") { model.clearData() }
    }
}
