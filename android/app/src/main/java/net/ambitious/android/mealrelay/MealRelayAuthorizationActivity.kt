package net.ambitious.android.mealrelay

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import net.ambitious.android.mealrelay.authorization.ui.AuthorizationUiState
import net.ambitious.android.mealrelay.authorization.ui.MealRelayAuthorizationViewModel

@AndroidEntryPoint
class MealRelayAuthorizationActivity : ComponentActivity() {
  private val viewModel by viewModels<MealRelayAuthorizationViewModel>()
  private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
  private val startAuthorizationIntent = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { activityResult ->
    if (activityResult.resultCode != RESULT_OK || activityResult.data == null) {
      viewModel.fail()
    } else {
      val code = runCatching {
        authorizationClient.getAuthorizationResultFromIntent(activityResult.data).serverAuthCode
      }.getOrNull()
      viewModel.complete(code)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch {
      repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        viewModel.state.collectLatest { state ->
          when (state) {
            is AuthorizationUiState.RequestAuthorization -> startAuthorization(state)
            AuthorizationUiState.Failed -> showAuthorizationFailure()
            AuthorizationUiState.Finished -> finish()
            AuthorizationUiState.Idle,
            AuthorizationUiState.AwaitingResult -> Unit
          }
        }
      }
    }
    viewModel.begin()
  }

  private fun startAuthorization(state: AuthorizationUiState.RequestAuthorization) {
    viewModel.requestLaunched()
    lifecycleScope.launch {
      try {
        val result = authorizationClient.authorize(state.request).await()
        if (result.hasResolution()) {
          val intentSender = checkNotNull(result.pendingIntent).intentSender
          startAuthorizationIntent.launch(IntentSenderRequest.Builder(intentSender).build())
        } else {
          viewModel.complete(result.serverAuthCode)
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        viewModel.fail()
      }
    }
  }

  private fun showAuthorizationFailure() {
    if (isFinishing) return
    AlertDialog.Builder(this)
      .setMessage(R.string.authorization_failed)
      .setPositiveButton(R.string.authorization_retry) { _, _ -> viewModel.begin() }
      .setNegativeButton(R.string.authorization_close) { _, _ -> finish() }
      .show()
  }
}
