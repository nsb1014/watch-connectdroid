package com.watchrelay.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.watchrelay.core.model.HeartSample
import com.watchrelay.core.model.TrackPoint
import com.watchrelay.core.model.Workout
import com.watchrelay.core.model.derivedHeartSamples

@Composable
fun WorkoutPane(state: UiState, id: String, modifier: Modifier) {
    val stored = state.workouts.firstOrNull { it.workout.id == id }
    if (stored == null) {
        Column(modifier.padding(20.dp)) { Text("Workout not found.") }
        return
    }
    val workout = stored.workout
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Summary", "Route", "Heart", "Splits", "Raw")
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(workout.name, style = MaterialTheme.typography.headlineLarge)
        Text("${Formatters.sport(workout.sport)}  ·  ${Formatters.formatLabel(workout.sourceFormat)}  ·  ${workout.sourceFileName}", style = MaterialTheme.typography.bodyMedium)
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Ink, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> SummaryTab(workout)
            1 -> RouteTab(workout.track)
            2 -> HeartTab(workout)
            3 -> SplitsTab(workout)
            else -> RawTab(workout)
        }
    }
}

@Composable
private fun SummaryTab(workout: Workout) {
    RelayCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("When", Formatters.whenStarted(workout.startMillis))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Time", Formatters.duration(workout.durationMillis))
            Stat("Distance", Formatters.distance(workout.distanceMeters))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Calories", Formatters.calories(workout.caloriesKcal))
            Stat("Avg HR", Formatters.hr(workout.avgHeartRateBpm))
            Stat("Max HR", Formatters.hr(workout.maxHeartRateBpm))
        }
        Text("${workout.track.size} GPS points  ·  ${workout.heartSamples.size} HR samples  ·  ${workout.splits.size} splits", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RouteTab(track: List<TrackPoint>) {
    val points = track.filter { it.latitude != null && it.longitude != null }
    RelayCard {
        Text("Route", style = MaterialTheme.typography.titleMedium)
        if (points.size < 2) {
            Text("This file has no GPS track. Apple Health XML often ships routes in a separate file inside the zip.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelSoft)
            ) {
                val lats = points.map { it.latitude!! }
                val lons = points.map { it.longitude!! }
                val minLat = lats.min()
                val maxLat = lats.max()
                val minLon = lons.min()
                val maxLon = lons.max()
                val pad = 24f
                fun x(lon: Double) = pad + ((lon - minLon) / (maxLon - minLon).coerceAtLeast(0.000001) * (size.width - pad * 2)).toFloat()
                fun y(lat: Double) = size.height - pad - ((lat - minLat) / (maxLat - minLat).coerceAtLeast(0.000001) * (size.height - pad * 2)).toFloat()
                val path = Path()
                path.moveTo(x(points.first().longitude!!), y(points.first().latitude!!))
                points.drop(1).forEach { path.lineTo(x(it.longitude!!), y(it.latitude!!)) }
                drawPath(path, color = Mint, style = Stroke(width = 6f, cap = StrokeCap.Round))
                drawCircle(Sky, 10f, Offset(x(points.first().longitude!!), y(points.first().latitude!!)))
                drawCircle(Amber, 10f, Offset(x(points.last().longitude!!), y(points.last().latitude!!)))
            }
            Text("${points.size} points", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HeartTab(workout: Workout) {
    val samples = workout.derivedHeartSamples()
    RelayCard {
        Text("Heart rate", style = MaterialTheme.typography.titleMedium)
        if (samples.size < 2) {
            Text("No heart-rate series in this file. Avg ${Formatters.hr(workout.avgHeartRateBpm)}.", style = MaterialTheme.typography.bodyMedium)
        } else {
            HeartChart(samples)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Samples", samples.size.toString())
                Stat("Avg", Formatters.hr(workout.avgHeartRateBpm))
                Stat("Max", Formatters.hr(workout.maxHeartRateBpm))
            }
        }
    }
}

@Composable
private fun HeartChart(samples: List<HeartSample>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PanelSoft)
    ) {
        val min = samples.minOf { it.bpm }.toFloat()
        val max = samples.maxOf { it.bpm }.toFloat().coerceAtLeast(min + 1f)
        val pad = 16f
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = pad + index.toFloat() / (samples.size - 1) * (size.width - pad * 2)
            val y = size.height - pad - ((sample.bpm - min) / (max - min) * (size.height - pad * 2))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFFFF6B8A), style = Stroke(width = 5f, cap = StrokeCap.Round))
    }
}

@Composable
private fun SplitsTab(workout: Workout) {
    RelayCard {
        Text("Splits", style = MaterialTheme.typography.titleMedium)
        if (workout.splits.isEmpty()) {
            Text("No lap or split records in this export.", style = MaterialTheme.typography.bodyMedium)
        } else {
            workout.splits.forEach { split ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lap ${split.index}", color = TextMain)
                    Text(
                        listOf(
                            Formatters.duration(split.durationMillis),
                            Formatters.distance(split.distanceMeters),
                            Formatters.hr(split.avgHeartRateBpm)
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun RawTab(workout: Workout) {
    RelayCard {
        Text("Original export", style = MaterialTheme.typography.titleMedium)
        Text(workout.sourceFileName + " · " + Formatters.formatLabel(workout.sourceFormat), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = workout.rawPreview.ifBlank { "(binary or empty preview)" },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMain
        )
    }
}
