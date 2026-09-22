package net.ambitious.android.mealrelay

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import net.ambitious.android.mealrelay.authorization.api.MealRelayAuthorizationApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MealRelayBackendClient(
  private val readToken: () -> String?,
  endpoint: String = BuildConfig.MEAL_RELAY_AUTH_ENDPOINT,
  httpClient: OkHttpClient = OkHttpClient(),
) {
  private val retrofit by lazy {
    val endpointUrl = endpoint.toHttpUrl()
    require(endpointUrl.isHttps)
    Retrofit.Builder()
      .baseUrl(endpointUrl.newBuilder().encodedPath("/").build())
      .client(httpClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
  }

  val authorization: MealRelayAuthorizationApi = retrofit.create(MealRelayAuthorizationApi::class.java)

  private val authenticatedRetrofit by lazy {
    val client = httpClient.newBuilder()
      .addInterceptor { chain ->
        check(chain.request().url.isHttps)
        val token = checkNotNull(readToken()) { "Google Health authorization is required" }
        val request = chain.request().newBuilder()
          .header("Authorization", "Bearer $token")
          .build()
        chain.proceed(request)
      }
      .build()
    retrofit.newBuilder().client(client).build()
  }

  fun <T> authenticatedService(service: Class<T>): T = authenticatedRetrofit.create(service)
}
