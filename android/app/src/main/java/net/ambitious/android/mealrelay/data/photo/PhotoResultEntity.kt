package net.ambitious.android.mealrelay.data.photo

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "photo_results", primaryKeys = ["version", "uri"])
data class PhotoResultEntity(
  val version: String,
  val uri: String,
  @ColumnInfo(name = "captured_at") val capturedAt: Long,
  @ColumnInfo(name = "is_food") val isFood: Boolean?,
)
