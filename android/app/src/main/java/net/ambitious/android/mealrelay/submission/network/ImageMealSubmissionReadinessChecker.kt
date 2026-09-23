package net.ambitious.android.mealrelay.submission.network

import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.MealSubmissionReadiness

class ImageMealSubmissionReadinessChecker(
  private val tokenStore: MealRelayTokenStore,
  private val endpoint: String,
) {
  fun check(submission: MealSubmissionEntity): MealSubmissionReadiness = when {
    tokenStore.read() == null -> MealSubmissionReadiness.AwaitingAuthorization
    submission.type != MealSubmissionEntity.TYPE_IMAGE || endpoint.isBlank() -> MealSubmissionReadiness.Unavailable
    else -> MealSubmissionReadiness.Ready
  }
}
