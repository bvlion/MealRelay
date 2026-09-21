package net.ambitious.android.mealrelay

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MealRelayAuthorizationActivity : ComponentActivity() {
  private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
  private var isWaitingForResolution = false
  private val startAuthorizationIntent = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { activityResult ->
    isWaitingForResolution = false
    if (activityResult.resultCode != RESULT_OK || activityResult.data == null) {
      showAuthorizationFailure()
    } else {
      lifecycleScope.launch {
        try {
          completeAuthorization(authorizationClient.getAuthorizationResultFromIntent(activityResult.data))
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          showAuthorizationFailure()
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    isWaitingForResolution = savedInstanceState?.getBoolean("waitingForResolution") ?: false
    if (!isWaitingForResolution) authorizeIfNeeded()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean("waitingForResolution", isWaitingForResolution)
    super.onSaveInstanceState(outState)
  }

  private fun authorizeIfNeeded() {
    lifecycleScope.launch {
      try {
        if (withContext(Dispatchers.IO) { MealRelayTokenStore(this@MealRelayAuthorizationActivity).read() } != null) {
          finish()
          return@launch
        }
        if (BuildConfig.MEAL_RELAY_OAUTH_CLIENT_ID.isBlank() || BuildConfig.MEAL_RELAY_AUTH_ENDPOINT.isBlank()) {
          showAuthorizationFailure()
          return@launch
        }
        val request = AuthorizationRequest.builder()
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
        val result = authorizationClient.authorize(request).await()
        if (result.hasResolution()) {
          val intentSender = checkNotNull(result.pendingIntent).intentSender
          isWaitingForResolution = true
          startAuthorizationIntent.launch(IntentSenderRequest.Builder(intentSender).build())
        } else {
          completeAuthorization(result)
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        showAuthorizationFailure()
      }
    }
  }

  private suspend fun completeAuthorization(result: AuthorizationResult) {
    val code = checkNotNull(result.serverAuthCode)
    val tokenStore = MealRelayTokenStore(this)
    MealRelayAuthorizationRepository(MealRelayBackendClient(tokenStore::read), tokenStore).complete(code)
    finish()
  }

  private fun showAuthorizationFailure() {
    if (isFinishing) return
    AlertDialog.Builder(this)
      .setMessage("Google Health の認可を完了できませんでした。")
      .setPositiveButton("再試行") { _, _ -> authorizeIfNeeded() }
      .setNegativeButton("閉じる") { _, _ -> finish() }
      .show()
  }
}
