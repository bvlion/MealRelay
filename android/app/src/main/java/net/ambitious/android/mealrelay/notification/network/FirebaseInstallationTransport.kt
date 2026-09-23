package net.ambitious.android.mealrelay.notification.network

import net.ambitious.android.mealrelay.notification.FirebaseInstallationRequest
import net.ambitious.android.mealrelay.notification.api.FirebaseInstallationApi
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FirebaseInstallationTransport(
  private val endpoint: String,
  private val httpClient: OkHttpClient = OkHttpClient(),
) {
  private val service by lazy {
    val endpointUrl = endpoint.toHttpUrl()
    require(endpointUrl.isHttps)
    Retrofit.Builder()
      .baseUrl(endpointUrl.newBuilder().encodedPath("/").build())
      .client(httpClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
      .create(FirebaseInstallationApi::class.java)
  }

  suspend fun register(token: String, fid: String) {
    val response = service.register(
      endpoint,
      "Bearer $token",
      FirebaseInstallationRequest(fid),
    )
    if (!response.isSuccessful) {
      throw FirebaseInstallationRegistrationException(
        response.code(),
        response.code() == 408 || response.code() == 429 || response.code() >= 500,
      )
    }
  }
}

class FirebaseInstallationRegistrationException(
  val statusCode: Int,
  val isRetryable: Boolean,
) : Exception("Firebase Installation registration failed with HTTP $statusCode")
