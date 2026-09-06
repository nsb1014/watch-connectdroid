package com.watchrelay.core

import com.google.common.truth.Truth.assertThat
import com.watchrelay.core.export.GpxWriter
import com.watchrelay.core.export.StravaSport
import com.watchrelay.core.importing.CsvWorkoutParser
import com.watchrelay.core.importing.FitParser
import com.watchrelay.core.importing.GpxParser
import com.watchrelay.core.importing.HealthXmlParser
import com.watchrelay.core.importing.ImportCoordinator
import com.watchrelay.core.importing.JsonWorkoutParser
import com.watchrelay.core.importing.TcxParser
import com.watchrelay.core.model.Sport
import org.junit.Test

class ParsersTest {
    @Test
    fun gpxExtractsTrackHeartRateAndName() {
        val workout = GpxParser.parse("run.gpx", Fixture.bytes("run.gpx")).single()
        assertThat(workout.name).isEqualTo("Morning Run")
        assertThat(workout.sport).isEqualTo(Sport.RUN)
        assertThat(workout.track).hasSize(2)
        assertThat(workout.track.first().heartRateBpm).isEqualTo(148)
        assertThat(workout.track.first().latitude).isWithin(0.0001).of(37.77490)
        assertThat(workout.durationMillis).isEqualTo(5 * 60 * 1000)
        assertThat(workout.distanceMeters!!).isGreaterThan(100.0)
    }

    @Test
    fun tcxExtractsLapsAndSport() {
        val workout = TcxParser.parse("bike.tcx", Fixture.bytes("bike.tcx")).single()
        assertThat(workout.sport).isEqualTo(Sport.CYCLE)
        assertThat(workout.distanceMeters).isEqualTo(4000.0)
        assertThat(workout.caloriesKcal).isEqualTo(180.0)
        assertThat(workout.splits).hasSize(1)
        assertThat(workout.track).hasSize(2)
        assertThat(workout.avgHeartRateBpm).isEqualTo(133)
    }

    @Test
    fun fitExtractsSessionAndRecord() {
        val workout = FitParser.parse("session.fit", FitBytes.sessionWithRecord()).single()
        assertThat(workout.sport).isEqualTo(Sport.RUN)
        assertThat(workout.durationMillis).isEqualTo(1_800_000)
        assertThat(workout.distanceMeters).isWithin(0.1).of(5200.0)
        assertThat(workout.track).isNotEmpty()
        assertThat(workout.track.first().heartRateBpm).isEqualTo(150)
        assertThat(workout.track.first().latitude!!).isWithin(0.001).of(37.7749)
    }

    @Test
    fun healthXmlAttachesHeartRateSamples() {
        val workout = HealthXmlParser.parse("export.xml", Fixture.bytes("export.xml")).single()
        assertThat(workout.sport).isEqualTo(Sport.RUN)
        assertThat(workout.distanceMeters).isWithin(0.1).of(5200.0)
        assertThat(workout.durationMillis).isEqualTo(30 * 60 * 1000)
        assertThat(workout.heartSamples).hasSize(2)
        assertThat(workout.avgHeartRateBpm).isEqualTo(155)
    }

    @Test
    fun jsonReadsNestedHealthAutoExport() {
        val workout = JsonWorkoutParser.parse("workouts.json", Fixture.bytes("workouts.json")).single()
        assertThat(workout.sport).isEqualTo(Sport.WALK)
        assertThat(workout.distanceMeters).isEqualTo(1600.0)
        assertThat(workout.track).hasSize(2)
        assertThat(workout.splits).hasSize(1)
        assertThat(workout.heartSamples).hasSize(2)
    }

    @Test
    fun csvReadsOneWorkoutPerRow() {
        val workouts = CsvWorkoutParser.parse("workouts.csv", Fixture.bytes("workouts.csv"))
        assertThat(workouts).hasSize(2)
        assertThat(workouts[0].sport).isEqualTo(Sport.YOGA)
        assertThat(workouts[1].sport).isEqualTo(Sport.STRENGTH)
        assertThat(workouts[0].avgHeartRateBpm).isEqualTo(82)
        assertThat(workouts[0].durationMillis).isEqualTo(40 * 60 * 1000)
    }

    @Test
    fun coordinatorImportsHealthZip() {
        val zip = Fixture.zip(mapOf("apple_health_export/export.xml" to Fixture.bytes("export.xml")))
        val workouts = ImportCoordinator.import("export.zip", zip)
        assertThat(workouts).hasSize(1)
        assertThat(workouts.single().sourceFileName).isEqualTo("export.xml")
    }

    @Test
    fun gpxWriterAndStravaTypeRoundTrip() {
        val workout = GpxParser.parse("run.gpx", Fixture.bytes("run.gpx")).single()
        val xml = GpxWriter.write(workout)
        assertThat(xml).contains("<trkpt")
        assertThat(xml).contains("Morning Run")
        assertThat(StravaSport.activityType(Sport.RUN)).isEqualTo("Run")
        val again = GpxParser.parse("out.gpx", xml.toByteArray()).single()
        assertThat(again.track).hasSize(workout.track.size)
    }
}
