package net.ambitious.android.mealrelay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.authorization.model.AuthorizationCode
import net.ambitious.android.mealrelay.authorization.network.MealRelayAuthorizationTransport
import javax.inject.Inject

class MealRelayAuthorizationRepository @Inject constructor(
  private val authorizationTransport: MealRelayAuthorizationTransport,
  private val tokenStore: MealRelayTokenStore,
) {
  suspend fun complete(authorizationCode: String) = withContext(Dispatchers.IO) {
    val result = authorizationTransport.exchange(AuthorizationCode(authorizationCode))
    tokenStore.write(result.token)
  }
}
