package net.ambitious.android.mealrelay.notification.network

import net.ambitious.android.mealrelay.notification.FirebaseInstallationRequest
import net.ambitious.android.mealrelay.notification.api.FirebaseInstallationApi
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FirebaseInstallationTransport(private val endpoint: String) {
  private val service by lazy {
    val endpointUrl = endpoint.toHttpUrl()
    require(endpointUrl.isHttps)
    Retrofit.Builder()
      .baseUrl(endpointUrl.newBuilder().encodedPath("/").build())
      .client(OkHttpClient())
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
    check(response.isSuccessful)
  }
}
