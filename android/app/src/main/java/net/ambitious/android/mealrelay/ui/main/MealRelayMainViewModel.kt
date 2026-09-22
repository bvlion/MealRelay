package net.ambitious.android.mealrelay.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.submission.MealSubmissionWorkScheduler
import javax.inject.Inject

@HiltViewModel
class MealRelayMainViewModel @Inject constructor(
  private val tokenStore: MealRelayTokenStore,
  private val mealSubmissionWorkScheduler: MealSubmissionWorkScheduler,
) : ViewModel() {
  private val mutableAuthorizationState = MutableStateFlow(MainAuthorizationState.Idle)
  val authorizationState: StateFlow<MainAuthorizationState> = mutableAuthorizationState.asStateFlow()

  fun recoverPendingSubmissions() {
    viewModelScope.launch { mealSubmissionWorkScheduler.resumePendingSubmissions() }
  }

  fun requestAuthorization() {
    viewModelScope.launch {
      if (withContext(Dispatchers.IO) { tokenStore.read() } == null) {
        mutableAuthorizationState.value = MainAuthorizationState.Required
      }
    }
  }

  fun consumeAuthorizationRequest() {
    mutableAuthorizationState.value = MainAuthorizationState.Idle
  }
}

enum class MainAuthorizationState { Idle, Required }
