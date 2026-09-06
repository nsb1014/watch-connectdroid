package com.watchrelay.core.importing

import com.watchrelay.core.model.FitnessFormat
import java.nio.charset.Charset
import java.util.Locale

object FormatDetector {
    fun detect(fileName: String, bytes: ByteArray): FitnessFormat {
        val lower = fileName.lowercase(Locale.US)
        if (bytes.size >= 12 && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == ".FIT") {
            return FitnessFormat.FIT
        }
        if (lower.endsWith(".fit")) return FitnessFormat.FIT
        if (isZip(bytes) || lower.endsWith(".zip")) return FitnessFormat.HEALTH_ZIP

        val head = preview(bytes)
        return when {
            lower.endsWith(".gpx") || looksLikeGpx(head) -> FitnessFormat.GPX
            lower.endsWith(".tcx") || looksLikeTcx(head) -> FitnessFormat.TCX
            lower.endsWith(".csv") || looksLikeCsv(head, lower) -> FitnessFormat.CSV
            lower.endsWith(".json") || looksLikeJson(head) -> FitnessFormat.JSON
            lower.endsWith(".xml") && looksLikeHealth(head) -> FitnessFormat.HEALTH_XML
            looksLikeHealth(head) -> FitnessFormat.HEALTH_XML
            looksLikeGpx(head) -> FitnessFormat.GPX
            looksLikeTcx(head) -> FitnessFormat.TCX
            looksLikeJson(head) -> FitnessFormat.JSON
            else -> FitnessFormat.UNKNOWN
        }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

    private fun preview(bytes: ByteArray): String {
        val limit = minOf(bytes.size, 4096)
        return String(bytes, 0, limit, Charset.forName("UTF-8")).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    }

    private fun looksLikeGpx(head: String): Boolean =
        head.contains("<gpx", ignoreCase = true)

    private fun looksLikeTcx(head: String): Boolean =
        head.contains("TrainingCenterDatabase", ignoreCase = true) ||
            head.contains("<tcx:", ignoreCase = true)

    private fun looksLikeHealth(head: String): Boolean =
        head.contains("<HealthData", ignoreCase = true) ||
            head.contains("HKWorkoutActivityType", ignoreCase = true)

    private fun looksLikeJson(head: String): Boolean {
        val start = head.trimStart()
        return start.startsWith("{") || start.startsWith("[")
    }

    private fun looksLikeCsv(head: String, fileName: String): Boolean {
        if (fileName.endsWith(".csv")) return true
        val first = head.lineSequence().firstOrNull().orEmpty().lowercase(Locale.US)
        return first.contains("duration") && (first.contains("distance") || first.contains("type"))
    }
}
