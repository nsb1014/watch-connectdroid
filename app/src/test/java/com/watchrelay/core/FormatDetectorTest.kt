package com.watchrelay.core

import com.google.common.truth.Truth.assertThat
import com.watchrelay.core.importing.FormatDetector
import com.watchrelay.core.model.FitnessFormat
import org.junit.Test

class FormatDetectorTest {
    @Test
    fun detectsGpxByExtensionAndContent() {
        val bytes = Fixture.bytes("run.gpx")
        assertThat(FormatDetector.detect("run.gpx", bytes)).isEqualTo(FitnessFormat.GPX)
        assertThat(FormatDetector.detect("untitled", bytes)).isEqualTo(FitnessFormat.GPX)
    }

    @Test
    fun detectsTcx() {
        assertThat(FormatDetector.detect("bike.tcx", Fixture.bytes("bike.tcx"))).isEqualTo(FitnessFormat.TCX)
    }

    @Test
    fun detectsFitMagic() {
        val bytes = FitBytes.sessionWithRecord()
        assertThat(FormatDetector.detect("session.fit", bytes)).isEqualTo(FitnessFormat.FIT)
        assertThat(FormatDetector.detect("no-ext", bytes)).isEqualTo(FitnessFormat.FIT)
    }

    @Test
    fun detectsHealthXmlAndZip() {
        assertThat(FormatDetector.detect("export.xml", Fixture.bytes("export.xml")))
            .isEqualTo(FitnessFormat.HEALTH_XML)
        val zip = Fixture.zip(mapOf("apple_health_export/export.xml" to Fixture.bytes("export.xml")))
        assertThat(FormatDetector.detect("export.zip", zip)).isEqualTo(FitnessFormat.HEALTH_ZIP)
    }

    @Test
    fun detectsJsonAndCsv() {
        assertThat(FormatDetector.detect("workouts.json", Fixture.bytes("workouts.json")))
            .isEqualTo(FitnessFormat.JSON)
        assertThat(FormatDetector.detect("workouts.csv", Fixture.bytes("workouts.csv")))
            .isEqualTo(FitnessFormat.CSV)
    }
}
