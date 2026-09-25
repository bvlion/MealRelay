package net.ambitious.android.mealrelay.submission.network.model

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity

class ImageMealSubmissionRequestFactory {
  fun create(
    submission: MealSubmissionEntity,
    images: List<ImageMealSubmissionImage>,
  ) = ImageMealSubmissionRequest(
    images = images,
    capturedAt = submission.occurredAt,
    mealId = submission.mealId,
  )
}
