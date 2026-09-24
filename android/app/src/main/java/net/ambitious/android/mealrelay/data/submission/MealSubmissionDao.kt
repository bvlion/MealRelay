package net.ambitious.android.mealrelay.data.submission

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MealSubmissionDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  fun insert(submission: MealSubmissionEntity)

  @Query("SELECT * FROM meal_submission_queue WHERE meal_id = :mealId")
  fun get(mealId: String): MealSubmissionEntity?

  @Query("SELECT * FROM meal_submission_queue WHERE type = :type AND state = :state AND automatic_attempt_count = 0 ORDER BY created_at")
  fun getPendingUnattemptedImages(type: String, state: String): List<MealSubmissionEntity>

  @Query("UPDATE meal_submission_queue SET image_uri = :imagePayload, occurred_at = :occurredAt, next_automatic_attempt_at = :submissionAt WHERE meal_id = :mealId AND state = :state AND automatic_attempt_count = 0")
  fun updatePendingImagePayload(mealId: String, imagePayload: String, occurredAt: String, submissionAt: Long, state: String): Int

  @Query("SELECT * FROM meal_submission_queue WHERE state = :state ORDER BY created_at")
  fun getWithState(state: String): List<MealSubmissionEntity>

  @Query("UPDATE meal_submission_queue SET automatic_attempt_count = automatic_attempt_count + 1, state = :sendingState, next_automatic_attempt_at = :interruptedRetryAt WHERE meal_id = :mealId AND state = :pendingState AND automatic_attempt_count < :maximumAttempts")
  fun beginAutomaticAttempt(
    mealId: String,
    pendingState: String,
    sendingState: String,
    maximumAttempts: Int,
    interruptedRetryAt: Long,
  ): Int

  @Query("UPDATE meal_submission_queue SET state = :pendingState WHERE meal_id = :mealId AND state = :sendingState")
  fun recoverInterruptedAutomaticAttempt(mealId: String, sendingState: String, pendingState: String): Int

  @Query("UPDATE meal_submission_queue SET state = :pendingState, next_automatic_attempt_at = :nextAttemptAt WHERE meal_id = :mealId AND state = :sendingState AND automatic_attempt_count = :attemptCount")
  fun recordRetry(mealId: String, attemptCount: Int, nextAttemptAt: Long, sendingState: String, pendingState: String): Int

  @Query("UPDATE meal_submission_queue SET state = :failedState, next_automatic_attempt_at = NULL, manual_retry_available = :isManualRetryAvailable WHERE meal_id = :mealId AND state IN (:pendingState, :sendingState) AND automatic_attempt_count = :attemptCount")
  fun recordFailure(
    mealId: String,
    attemptCount: Int,
    isManualRetryAvailable: Boolean,
    pendingState: String,
    sendingState: String,
    failedState: String,
  ): Int

  @Query("DELETE FROM meal_submission_queue WHERE meal_id = :mealId")
  fun delete(mealId: String)
}
