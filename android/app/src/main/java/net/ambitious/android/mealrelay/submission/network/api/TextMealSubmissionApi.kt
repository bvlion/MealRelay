package net.ambitious.android.mealrelay.submission.network.api

import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequest
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

internal interface TextMealSubmissionApi {
  @POST
  suspend fun submit(@Url endpoint: String, @Body request: TextMealSubmissionRequest)
}
