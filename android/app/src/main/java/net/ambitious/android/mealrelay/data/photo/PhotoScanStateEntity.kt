package net.ambitious.android.mealrelay.data.photo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_scan_state")
data class PhotoScanStateEntity(
  @PrimaryKey val id: Int = 1,
  val version: String?,
  val generation: Long,
  @ColumnInfo(name = "enrolled_generation") val enrolledGeneration: Long = generation,
  @ColumnInfo(name = "enrolled_at") val enrolledAt: Long,
)
