package net.ambitious.android.mealrelay.authorization.network

import net.ambitious.android.mealrelay.authorization.api.MealRelayAuthorizationApi
import net.ambitious.android.mealrelay.authorization.model.AuthorizationCode
import net.ambitious.android.mealrelay.authorization.model.DeviceToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MealRelayAuthorizationTransport(
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
      .create(MealRelayAuthorizationApi::class.java)
  }

  suspend fun exchange(authorizationCode: AuthorizationCode): DeviceToken =
    service.exchange(endpoint, authorizationCode)
}
