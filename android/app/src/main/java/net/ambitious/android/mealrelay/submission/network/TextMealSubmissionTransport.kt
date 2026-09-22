package net.ambitious.android.mealrelay.submission.network

import net.ambitious.android.mealrelay.submission.network.api.TextMealSubmissionApi
import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TextMealSubmissionTransport(
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
        chain.proceed(
          chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build(),
        )
      }
      .build()
    Retrofit.Builder()
      .baseUrl(endpointUrl.newBuilder().encodedPath("/").build())
      .client(authenticatedClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
      .create(TextMealSubmissionApi::class.java)
  }

  suspend fun submit(request: TextMealSubmissionRequest) {
    service.submit(endpoint, request)
  }
}
