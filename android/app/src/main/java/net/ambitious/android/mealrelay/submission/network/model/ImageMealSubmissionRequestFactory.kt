package net.ambitious.android.mealrelay.submission.network.model

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity

class ImageMealSubmissionRequestFactory {
  fun create(
    submission: MealSubmissionEntity,
    image: ImageMealSubmissionImage,
  ) = ImageMealSubmissionRequest(
    image = image,
    capturedAt = submission.occurredAt,
    mealId = submission.mealId,
  )
}
