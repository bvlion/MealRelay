package net.ambitious.android.mealrelay.data

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
import androidx.room.Update

@Entity(tableName = "photo_results", primaryKeys = ["version", "uri"])
data class PhotoResultEntity(
  val version: String,
  val uri: String,
  @ColumnInfo(name = "captured_at") val capturedAt: Long,
  @ColumnInfo(name = "is_food") val isFood: Boolean?,
)

@Entity(tableName = "photo_scan_state")
data class PhotoScanStateEntity(
  @PrimaryKey val id: Int = 1,
  val version: String?,
  val generation: Long,
  @ColumnInfo(name = "enrolled_generation") val enrolledGeneration: Long = generation,
  @ColumnInfo(name = "enrolled_at") val enrolledAt: Long,
)

@Entity(tableName = "meal_submission_queue")
data class MealSubmissionEntity(
  @PrimaryKey @ColumnInfo(name = "meal_id") val mealId: String,
  val type: String,
  @ColumnInfo(name = "image_uri") val imageUri: String?,
  val text: String?,
  @ColumnInfo(name = "occurred_at") val occurredAt: String,
  @ColumnInfo(name = "automatic_attempt_count") val automaticAttemptCount: Int = 0,
  val state: String = STATE_PENDING,
  @ColumnInfo(name = "next_automatic_attempt_at") val nextAutomaticAttemptAt: Long? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
) {
  companion object {
    const val TYPE_IMAGE = "image"
    const val TYPE_TEXT = "text"
    const val STATE_PENDING = "pending"
    const val STATE_FAILED = "failed"
  }
}

@Dao
interface PhotoProcessingDao {
  @Query("SELECT * FROM photo_scan_state WHERE id = 1")
  fun getScanState(): PhotoScanStateEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  fun insertScanState(state: PhotoScanStateEntity)

  @Update
  fun updateScanState(state: PhotoScanStateEntity)

  @Query("SELECT EXISTS(SELECT 1 FROM photo_results WHERE version = :version AND uri = :uri)")
  fun hasResult(version: String, uri: String): Boolean

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  fun insertResult(result: PhotoResultEntity)

  @Query("SELECT * FROM photo_results ORDER BY version, uri")
  fun getResults(): List<PhotoResultEntity>
}

@Dao
interface MealSubmissionDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  fun insert(submission: MealSubmissionEntity)

  @Query("SELECT * FROM meal_submission_queue WHERE meal_id = :mealId")
  fun get(mealId: String): MealSubmissionEntity?

  @Query("SELECT * FROM meal_submission_queue WHERE state = :state ORDER BY created_at")
  fun getWithState(state: String): List<MealSubmissionEntity>

  @Query("UPDATE meal_submission_queue SET automatic_attempt_count = :attemptCount, next_automatic_attempt_at = :nextAttemptAt WHERE meal_id = :mealId")
  fun recordRetry(mealId: String, attemptCount: Int, nextAttemptAt: Long)

  @Query("UPDATE meal_submission_queue SET automatic_attempt_count = :attemptCount, state = :state, next_automatic_attempt_at = NULL WHERE meal_id = :mealId")
  fun recordFailure(mealId: String, attemptCount: Int, state: String)

  @Query("DELETE FROM meal_submission_queue WHERE meal_id = :mealId")
  fun delete(mealId: String)
}

@Database(
  entities = [PhotoResultEntity::class, PhotoScanStateEntity::class, MealSubmissionEntity::class],
  version = 1,
  exportSchema = true,
)
abstract class MealRelayDatabase : RoomDatabase() {
  abstract fun photoProcessingDao(): PhotoProcessingDao
  abstract fun mealSubmissionDao(): MealSubmissionDao

  companion object {
    @Volatile private var instance: MealRelayDatabase? = null

    fun get(context: Context): MealRelayDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext,
        MealRelayDatabase::class.java,
        "meal_relay.db",
      ).build().also { instance = it }
    }
  }
}
