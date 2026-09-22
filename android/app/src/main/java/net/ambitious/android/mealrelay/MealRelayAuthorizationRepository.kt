package net.ambitious.android.mealrelay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.authorization.model.AuthorizationCode
import javax.inject.Inject

class MealRelayAuthorizationRepository @Inject constructor(
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
