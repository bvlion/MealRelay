package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.MealSubmissionDao
import net.ambitious.android.mealrelay.data.MealSubmissionEntity
import java.util.UUID

class MealSubmissionRepository(private val dao: MealSubmissionDao) {
  fun enqueue(draft: MealSubmissionDraft, createdAt: Long): String {
    val mealId = UUID.randomUUID().toString()
    dao.insert(
      MealSubmissionEntity(
        mealId = mealId,
        type = draft.type,
        imageUri = draft.imageUri,
        text = draft.text,
        occurredAt = draft.occurredAt,
        createdAt = createdAt,
      ),
    )
    return mealId
  }

  fun get(mealId: String): MealSubmissionEntity? = dao.get(mealId)

  fun failedSubmissions(): List<MealSubmissionEntity> = dao.getWithState(MealSubmissionEntity.STATE_FAILED)

  fun pendingSubmissions(): List<MealSubmissionEntity> = dao.getWithState(MealSubmissionEntity.STATE_PENDING)

  fun delete(mealId: String) {
    dao.delete(mealId)
  }

  fun recordAutomaticRetry(mealId: String, attemptCount: Int, nextAttemptAt: Long) {
    dao.recordRetry(mealId, attemptCount, nextAttemptAt)
  }

  fun recordAutomaticFailure(mealId: String, attemptCount: Int) {
    dao.recordFailure(mealId, attemptCount, MealSubmissionEntity.STATE_FAILED)
  }
}

data class MealSubmissionDraft(
  val type: String,
  val imageUri: String?,
  val text: String?,
  val occurredAt: String,
)
