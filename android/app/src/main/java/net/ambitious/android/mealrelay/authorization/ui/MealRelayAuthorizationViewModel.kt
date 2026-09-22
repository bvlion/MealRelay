package net.ambitious.android.mealrelay.authorization.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.MealRelayAuthorizationRepository
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.authorization.GoogleHealthAuthorizationRequestFactory
import net.ambitious.android.mealrelay.submission.MealSubmissionWorkScheduler
import javax.inject.Inject

@HiltViewModel
class MealRelayAuthorizationViewModel @Inject constructor(
  private val tokenStore: MealRelayTokenStore,
  private val authorizationRepository: MealRelayAuthorizationRepository,
  private val mealSubmissionWorkScheduler: MealSubmissionWorkScheduler,
  private val requestFactory: GoogleHealthAuthorizationRequestFactory,
) : ViewModel() {
  private val mutableState = MutableStateFlow<AuthorizationUiState>(AuthorizationUiState.Idle)
  val state: StateFlow<AuthorizationUiState> = mutableState.asStateFlow()

  fun begin() {
    if (mutableState.value != AuthorizationUiState.Idle && mutableState.value != AuthorizationUiState.Failed) return
    viewModelScope.launch {
      if (withContext(Dispatchers.IO) { tokenStore.read() } != null) {
        mutableState.value = AuthorizationUiState.Finished
      } else {
        mutableState.value = AuthorizationUiState.RequestAuthorization(requestFactory.create())
      }
    }
  }

  fun requestLaunched() {
    mutableState.value = AuthorizationUiState.AwaitingResult
  }

  fun complete(authorizationCode: String?) {
    if (authorizationCode == null) {
      mutableState.value = AuthorizationUiState.Failed
      return
    }
    viewModelScope.launch {
      try {
        authorizationRepository.complete(authorizationCode)
        mealSubmissionWorkScheduler.resumePendingSubmissions()
        mutableState.value = AuthorizationUiState.Finished
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        mutableState.value = AuthorizationUiState.Failed
      }
    }
  }

  fun fail() {
    mutableState.value = AuthorizationUiState.Failed
  }
}

sealed interface AuthorizationUiState {
  data object Idle : AuthorizationUiState
  data class RequestAuthorization(val request: AuthorizationRequest) : AuthorizationUiState
  data object AwaitingResult : AuthorizationUiState
  data object Failed : AuthorizationUiState
  data object Finished : AuthorizationUiState
}
