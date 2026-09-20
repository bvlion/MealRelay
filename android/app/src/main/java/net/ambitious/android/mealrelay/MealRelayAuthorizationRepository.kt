package net.ambitious.android.mealrelay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

data class AuthorizationCode(val code: String)
data class DeviceToken(val token: String)

interface MealRelayAuthorizationApi {
  @POST
  suspend fun exchange(@Url endpoint: String, @Body body: AuthorizationCode): DeviceToken
}

class MealRelayAuthorizationRepository(
  private val backendClient: MealRelayBackendClient,
  private val tokenStore: MealRelayTokenStore,
) {
  suspend fun complete(authorizationCode: String) = withContext(Dispatchers.IO) {
    val result = backendClient.authorization.exchange(
      BuildConfig.MEAL_RELAY_AUTH_ENDPOINT,
      AuthorizationCode(authorizationCode),
    )
    tokenStore.write(result.token)
  }
}
