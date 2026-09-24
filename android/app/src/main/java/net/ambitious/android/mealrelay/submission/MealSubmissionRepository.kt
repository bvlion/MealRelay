package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.submission.MealSubmissionDao
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import javax.inject.Inject

class MealSubmissionRepository @Inject constructor(private val dao: MealSubmissionDao) {
  fun insert(submission: MealSubmissionEntity) = dao.insert(submission)

  fun get(mealId: String): MealSubmissionEntity? = dao.get(mealId)

  fun pendingUnattemptedImageSubmissions(): List<MealSubmissionEntity> = dao.getPendingUnattemptedImages(
    MealSubmissionEntity.TYPE_IMAGE,
    MealSubmissionEntity.STATE_PENDING,
  )

  fun updatePendingImagePayload(mealId: String, imagePayload: String, occurredAt: String, submissionAt: Long): Boolean = dao.updatePendingImagePayload(
    mealId,
    imagePayload,
    occurredAt,
    submissionAt,
    MealSubmissionEntity.STATE_PENDING,
  ) > 0

  fun failedSubmissions(): List<MealSubmissionEntity> = dao.getWithState(MealSubmissionEntity.STATE_FAILED)

  fun pendingSubmissions(): List<MealSubmissionEntity> = dao.getWithState(MealSubmissionEntity.STATE_PENDING)

  fun delete(mealId: String) = dao.delete(mealId)

  fun beginAutomaticAttempt(
    mealId: String,
    maximumAttempts: Int,
    interruptedRetryAt: Long,
  ): MealSubmissionEntity? {
    if (dao.beginAutomaticAttempt(
        mealId,
        MealSubmissionEntity.STATE_PENDING,
        MealSubmissionEntity.STATE_SENDING,
        maximumAttempts,
        interruptedRetryAt,
      ) == 0
    ) return null
    return dao.get(mealId)
  }

  fun recoverInterruptedAutomaticAttempt(mealId: String) {
    dao.recoverInterruptedAutomaticAttempt(
      mealId,
      MealSubmissionEntity.STATE_SENDING,
      MealSubmissionEntity.STATE_PENDING,
    )
  }

  fun recordAutomaticRetry(mealId: String, attemptCount: Int, nextAttemptAt: Long) {
    dao.recordRetry(
      mealId,
      attemptCount,
      nextAttemptAt,
      MealSubmissionEntity.STATE_SENDING,
      MealSubmissionEntity.STATE_PENDING,
    )
  }

  fun recordAutomaticFailure(
    mealId: String,
    attemptCount: Int,
    isManualRetryAvailable: Boolean = true,
  ) {
    dao.recordFailure(
      mealId,
      attemptCount,
      isManualRetryAvailable,
      MealSubmissionEntity.STATE_PENDING,
      MealSubmissionEntity.STATE_SENDING,
      MealSubmissionEntity.STATE_FAILED,
    )
  }
}
