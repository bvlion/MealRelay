package net.ambitious.android.mealrelay.submission.network.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

internal interface ImageMealSubmissionApi {
  @Multipart
  @POST
  suspend fun submit(
    @Url endpoint: String,
    @Part image: MultipartBody.Part,
    @Part("capturedAt") capturedAt: RequestBody,
    @Part("mealId") mealId: RequestBody,
  )
}
