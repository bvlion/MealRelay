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
  @ColumnInfo(name = "enrolled_at") val enrolledAt: Long,
)

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

@Database(entities = [PhotoResultEntity::class, PhotoScanStateEntity::class], version = 1, exportSchema = true)
abstract class PhotoProcessingDatabase : RoomDatabase() {
  abstract fun photoProcessingDao(): PhotoProcessingDao

  companion object {
    @Volatile private var instance: PhotoProcessingDatabase? = null

    fun get(context: Context): PhotoProcessingDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext,
        PhotoProcessingDatabase::class.java,
        "photo_detection.db",
      ).build().also { instance = it }
    }
  }
}
