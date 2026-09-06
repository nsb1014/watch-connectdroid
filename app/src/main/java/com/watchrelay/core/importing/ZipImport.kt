package com.watchrelay.core.importing

import com.watchrelay.core.model.Workout
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

object ZipImport {
    fun parse(fileName: String, bytes: ByteArray): List<Workout> {
        val workouts = mutableListOf<Workout>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                if (name.startsWith(".")) continue
                val content = zip.readBytes()
                if (content.isEmpty()) continue
                val format = FormatDetector.detect(name, content)
                if (format == com.watchrelay.core.model.FitnessFormat.HEALTH_ZIP) continue
                workouts += ImportCoordinator.import(name.ifBlank { fileName }, content)
            }
        }
        return workouts
    }
}
