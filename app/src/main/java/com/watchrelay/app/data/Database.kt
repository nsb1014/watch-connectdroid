package com.watchrelay.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sport: String,
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double?,
    val caloriesKcal: Double?,
    val avgHeartRateBpm: Int?,
    val maxHeartRateBpm: Int?,
    val sourceFormat: String,
    val sourceFileName: String,
    val notes: String?,
    val rawPreview: String,
    val originalPath: String?,
    val trackJson: String,
    val samplesJson: String,
    val splitsJson: String,
    val stravaUploadId: String?,
    @ColumnInfo(defaultValue = "")
    val stravaStatus: String?
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startMillis DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: String): WorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkoutEntity)

    @Query("UPDATE workouts SET stravaUploadId = :uploadId, stravaStatus = :status WHERE id = :id")
    suspend fun setStrava(id: String, uploadId: String?, status: String)

    @Query("DELETE FROM workouts")
    suspend fun clear()
}

@Database(entities = [WorkoutEntity::class], version = 1, exportSchema = false)
abstract class WatchRelayDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao

    companion object {
        fun create(context: Context): WatchRelayDatabase =
            Room.databaseBuilder(context, WatchRelayDatabase::class.java, "watchrelay.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
