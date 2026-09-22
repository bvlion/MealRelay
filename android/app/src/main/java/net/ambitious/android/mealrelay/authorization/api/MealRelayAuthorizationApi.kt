package net.ambitious.android.mealrelay.authorization.api

import net.ambitious.android.mealrelay.authorization.model.AuthorizationCode
import net.ambitious.android.mealrelay.authorization.model.DeviceToken
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface MealRelayAuthorizationApi {
  @POST
  suspend fun exchange(@Url endpoint: String, @Body body: AuthorizationCode): DeviceToken
}
