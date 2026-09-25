package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import javax.inject.Inject

class FoodPhotoMealGrouping @Inject constructor() {
  data class Group(
    val photos: List<ImageMealSubmissionPayload.Photo>,
    val submissions: List<MealSubmissionEntity>,
  )

  fun group(
    submissions: List<MealSubmissionEntity>,
    newPhoto: ImageMealSubmissionPayload.Photo,
  ): List<Group> {
    val photos = submissions.flatMap { submission ->
      ImageMealSubmissionPayload.decode(checkNotNull(submission.imagePayload)).photos
        .filter { it.capturedAt != null }
        .map { photo -> photo to submission }
    } + (newPhoto to null)
    val groups = mutableListOf<MutableList<Pair<ImageMealSubmissionPayload.Photo, MealSubmissionEntity?>>>()
    photos.sortedBy { it.first.capturedAt }.forEach { photo ->
      val capturedAt = checkNotNull(photo.first.capturedAt)
      val previousCapturedAt = groups.lastOrNull()?.lastOrNull()?.first?.capturedAt
      if (previousCapturedAt == null ||
        capturedAt - previousCapturedAt >= MealSubmissionQueue.FOOD_MEAL_WINDOW_MILLIS
      ) {
        groups.add(mutableListOf(photo))
      } else {
        groups.last().add(photo)
      }
    }
    return groups.map { photosInGroup ->
      Group(
        photos = photosInGroup.map { it.first },
        submissions = photosInGroup.mapNotNull { it.second }.distinctBy { it.mealId },
      )
    }
  }
}
