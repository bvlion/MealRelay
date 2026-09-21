package net.ambitious.android.mealrelay

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
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
  @ColumnInfo(name = "occurred_at") val occurredAt: Long,
  @ColumnInfo(name = "automatic_attempt_count") val automaticAttemptCount: Int = 0,
  val state: String = STATE_PENDING,
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

  @Insert(onConflict = OnConflictStrategy.ABORT)
  fun insertMealSubmission(submission: MealSubmissionEntity)

  @Query("SELECT * FROM meal_submission_queue WHERE state = :state ORDER BY created_at")
  fun getMealSubmissionsWithState(state: String): List<MealSubmissionEntity>

  @Query("SELECT * FROM meal_submission_queue WHERE meal_id = :mealId")
  fun getMealSubmission(mealId: String): MealSubmissionEntity?

  @Query("UPDATE meal_submission_queue SET automatic_attempt_count = automatic_attempt_count + 1 WHERE meal_id = :mealId")
  fun incrementAutomaticAttemptCount(mealId: String)

  @Query("UPDATE meal_submission_queue SET state = :state WHERE meal_id = :mealId")
  fun updateMealSubmissionState(mealId: String, state: String)

  @Query("DELETE FROM meal_submission_queue WHERE meal_id = :mealId")
  fun deleteMealSubmission(mealId: String)

  @Transaction
  fun insertResultAndFoodSubmission(result: PhotoResultEntity, submission: MealSubmissionEntity?) {
    insertResult(result)
    if (submission != null) {
      insertMealSubmission(submission)
    }
  }

  @Query("SELECT * FROM photo_results ORDER BY version, uri")
  fun getResults(): List<PhotoResultEntity>
}

@Database(
  entities = [PhotoResultEntity::class, PhotoScanStateEntity::class, MealSubmissionEntity::class],
  version = 3,
  exportSchema = true,
)
abstract class PhotoProcessingDatabase : RoomDatabase() {
  abstract fun photoProcessingDao(): PhotoProcessingDao

  companion object {
    @Volatile private var instance: PhotoProcessingDatabase? = null

    fun get(context: Context): PhotoProcessingDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext,
        PhotoProcessingDatabase::class.java,
        "photo_detection.db",
      ).addMigrations(MIGRATION_2_3).build().also { instance = it }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS meal_submission_queue " +
            "(meal_id TEXT NOT NULL, type TEXT NOT NULL, image_uri TEXT, text TEXT, " +
            "occurred_at INTEGER NOT NULL, automatic_attempt_count INTEGER NOT NULL, " +
            "state TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(meal_id))",
        )
      }
    }
  }
}
