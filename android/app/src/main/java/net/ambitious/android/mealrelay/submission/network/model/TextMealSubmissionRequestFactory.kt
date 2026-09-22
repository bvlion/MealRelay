package net.ambitious.android.mealrelay.submission.network.model

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity

class TextMealSubmissionRequestFactory {
  fun create(submission: MealSubmissionEntity) = TextMealSubmissionRequest(
    text = checkNotNull(submission.text),
    inputAt = submission.occurredAt,
    mealId = submission.mealId,
  )
}
