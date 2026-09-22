package net.ambitious.android.mealrelay.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.MealSubmissionDraft
import net.ambitious.android.mealrelay.submission.MealSubmissionQueue
import net.ambitious.android.mealrelay.submission.MealSubmissionWorkScheduler
import java.time.Clock
import java.time.OffsetDateTime
import javax.inject.Inject

@HiltViewModel
class MealRelayMainViewModel @Inject constructor(
  private val tokenStore: MealRelayTokenStore,
  private val mealSubmissionQueue: MealSubmissionQueue,
  private val mealSubmissionWorkScheduler: MealSubmissionWorkScheduler,
  private val clock: Clock,
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

  fun submitManualMeal(text: String): Job = viewModelScope.launch(Dispatchers.IO) {
    mealSubmissionQueue.enqueue(
      MealSubmissionDraft(
        type = MealSubmissionEntity.TYPE_TEXT,
        imageUri = null,
        text = text,
        occurredAt = OffsetDateTime.now(clock).toString(),
      ),
    )
  }
}

enum class MainAuthorizationState { Idle, Required }
