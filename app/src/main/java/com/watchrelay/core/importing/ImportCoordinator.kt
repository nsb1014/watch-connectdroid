package com.watchrelay.core.importing

import com.watchrelay.core.model.FitnessFormat
import com.watchrelay.core.model.Workout

object ImportCoordinator {
    fun import(fileName: String, bytes: ByteArray): List<Workout> {
        val format = FormatDetector.detect(fileName, bytes)
        val parsed = parseKnown(format, fileName, bytes)
        if (parsed.isNotEmpty()) return parsed
        val fallbacks = listOf(
            FitnessFormat.GPX,
            FitnessFormat.TCX,
            FitnessFormat.HEALTH_XML,
            FitnessFormat.JSON,
            FitnessFormat.CSV,
            FitnessFormat.FIT
        )
        for (candidate in fallbacks) {
            if (candidate == format) continue
            val attempt = runCatching { parseKnown(candidate, fileName, bytes) }.getOrDefault(emptyList())
            if (attempt.isNotEmpty()) return attempt
        }
        return emptyList()
    }

    private fun parseKnown(format: FitnessFormat, fileName: String, bytes: ByteArray): List<Workout> =
        runCatching {
            when (format) {
                FitnessFormat.GPX -> GpxParser.parse(fileName, bytes)
                FitnessFormat.TCX -> TcxParser.parse(fileName, bytes)
                FitnessFormat.FIT -> FitParser.parse(fileName, bytes)
                FitnessFormat.HEALTH_XML -> HealthXmlParser.parse(fileName, bytes)
                FitnessFormat.HEALTH_ZIP -> ZipImport.parse(fileName, bytes)
                FitnessFormat.JSON -> JsonWorkoutParser.parse(fileName, bytes)
                FitnessFormat.CSV -> CsvWorkoutParser.parse(fileName, bytes)
                FitnessFormat.UNKNOWN -> emptyList()
            }
        }.getOrDefault(emptyList())
}
