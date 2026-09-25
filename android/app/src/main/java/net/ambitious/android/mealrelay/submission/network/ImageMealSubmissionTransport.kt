package net.ambitious.android.mealrelay.submission.network

import net.ambitious.android.mealrelay.submission.network.api.ImageMealSubmissionApi
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit

class ImageMealSubmissionTransport(
  private val readToken: () -> String?,
  private val endpoint: String,
  private val httpClient: OkHttpClient = OkHttpClient(),
) {
  private val service by lazy {
    val endpointUrl = endpoint.toHttpUrl()
    require(endpointUrl.isHttps)
    val authenticatedClient = httpClient.newBuilder()
      .addInterceptor { chain ->
        check(chain.request().url.isHttps)
        val token = checkNotNull(readToken()) { "Google Health authorization is required" }
        chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
      }
      .build()
    Retrofit.Builder()
      .baseUrl(endpointUrl.newBuilder().encodedPath("/").build())
      .client(authenticatedClient)
      .build()
      .create(ImageMealSubmissionApi::class.java)
  }

  suspend fun submit(request: ImageMealSubmissionRequest) {
    service.submit(
      endpoint,
      request.images.mapIndexed { index, image ->
        val imageMediaType = image.mediaType.toMediaTypeOrNull()
          ?: "application/octet-stream".toMediaType()
        MultipartBody.Part.createFormData(
          "image",
          "image-$index",
          image.bytes.toRequestBody(imageMediaType),
        )
      },
      request.capturedAt.toRequestBody("text/plain".toMediaType()),
      request.mealId.toRequestBody("text/plain".toMediaType()),
    )
  }
}
