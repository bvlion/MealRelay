package net.ambitious.android.mealrelay.data.submission

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    const val STATE_SENDING = "sending"
    const val STATE_FAILED = "failed"
  }
}
