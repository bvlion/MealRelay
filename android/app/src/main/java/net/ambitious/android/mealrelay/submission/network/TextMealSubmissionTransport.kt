package net.ambitious.android.mealrelay.submission.network

import net.ambitious.android.mealrelay.MealRelayBackendClient
import net.ambitious.android.mealrelay.submission.network.api.TextMealSubmissionApi
import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequest

class TextMealSubmissionTransport(
  private val backendClient: MealRelayBackendClient,
  private val endpoint: String,
) {
  suspend fun submit(request: TextMealSubmissionRequest) {
    backendClient.authenticatedService(TextMealSubmissionApi::class.java).submit(endpoint, request)
  }
}
