package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import java.util.UUID
import javax.inject.Inject

class MealSubmissionQueue @Inject constructor(
  private val repository: MealSubmissionRepository,
  private val scheduler: MealSubmissionWorkScheduler,
) {
  fun enqueue(draft: MealSubmissionDraft): String {
    val mealId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    repository.insert(MealSubmissionEntity(
      mealId = mealId,
      type = draft.type,
      imageUri = draft.imageUri,
      text = draft.text,
      occurredAt = draft.occurredAt,
      createdAt = now,
    ))
    scheduler.schedule(mealId, now)
    return mealId
  }
}

data class MealSubmissionDraft(
  val type: String,
  val imageUri: String?,
  val text: String?,
  val occurredAt: String,
)
