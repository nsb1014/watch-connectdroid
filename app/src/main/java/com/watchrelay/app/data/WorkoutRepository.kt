package com.watchrelay.app.data

import android.content.Context
import com.watchrelay.core.importing.ImportCoordinator
import com.watchrelay.core.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class WorkoutRepository(
    private val context: Context,
    private val dao: WorkoutDao
) {
    fun observeWorkouts(): Flow<List<StoredWorkout>> =
        dao.observeAll().map { rows ->
            rows.map { StoredWorkout(Codec.toModel(it), it.stravaStatus, it.stravaUploadId) }
        }

    suspend fun get(id: String): StoredWorkout? =
        dao.get(id)?.let { StoredWorkout(Codec.toModel(it), it.stravaStatus, it.stravaUploadId) }

    suspend fun importBytes(fileName: String, bytes: ByteArray): List<Workout> {
        val dir = File(context.filesDir, "imports").apply { mkdirs() }
        val safeName = fileName.ifBlank { "import.bin" }.replace('/', '_')
        val target = File(dir, "${System.currentTimeMillis()}-$safeName")
        target.writeBytes(bytes)
        val workouts = ImportCoordinator.import(fileName, bytes)
        workouts.forEach { workout ->
            dao.upsert(Codec.toEntity(workout, target.absolutePath))
        }
        return workouts
    }

    suspend fun markStrava(id: String, uploadId: String?, status: String) {
        dao.setStrava(id, uploadId, status)
    }

    suspend fun clear() {
        dao.clear()
        File(context.filesDir, "imports").deleteRecursively()
    }
}

data class StoredWorkout(
    val workout: Workout,
    val stravaStatus: String?,
    val stravaUploadId: String?
)
