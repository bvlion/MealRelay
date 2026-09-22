package net.ambitious.android.mealrelay.authorization

import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import net.ambitious.android.mealrelay.BuildConfig
import javax.inject.Inject

class GoogleHealthAuthorizationRequestFactory @Inject constructor() {
  fun create(): AuthorizationRequest = AuthorizationRequest.builder()
    .setRequestedScopes(
      listOf(
        Scope("openid"),
        Scope("email"),
        Scope("https://www.googleapis.com/auth/googlehealth.nutrition.writeonly"),
        Scope("https://www.googleapis.com/auth/googlehealth.nutrition.readonly"),
      ),
    )
    .requestOfflineAccess(BuildConfig.MEAL_RELAY_OAUTH_CLIENT_ID)
    .setPrompt(AuthorizationRequest.Prompt.CONSENT)
    .build()
}
