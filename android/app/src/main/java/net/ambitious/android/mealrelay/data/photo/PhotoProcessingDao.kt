package net.ambitious.android.mealrelay.data.photo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

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

  @Query("SELECT EXISTS(SELECT 1 FROM photo_results WHERE uri = :uri AND captured_at = :capturedAt AND is_food = 1)")
  fun hasFoodResult(uri: String, capturedAt: Long): Boolean

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  fun insertResult(result: PhotoResultEntity)

  @Query("SELECT * FROM photo_results ORDER BY version, uri")
  fun getResults(): List<PhotoResultEntity>
}
