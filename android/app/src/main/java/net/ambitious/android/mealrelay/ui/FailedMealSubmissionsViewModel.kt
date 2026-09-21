package net.ambitious.android.mealrelay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.data.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.AutomaticMealSubmission
import net.ambitious.android.mealrelay.submission.MealSubmissionRepository

class FailedMealSubmissionsViewModel(
  private val repository: MealSubmissionRepository,
  private val automaticMealSubmission: AutomaticMealSubmission,
) : ViewModel() {
  var state by mutableStateOf(FailedMealSubmissionsState())
    private set

  fun load() {
    viewModelScope.launch {
      state = FailedMealSubmissionsState(
        submissions = withContext(Dispatchers.IO) { repository.failedSubmissions() },
      )
    }
  }

  fun retry(mealId: String) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { automaticMealSubmission.submitManually(mealId) }
      load()
    }
  }

  companion object {
    fun factory(
      repository: MealSubmissionRepository,
      automaticMealSubmission: AutomaticMealSubmission,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FailedMealSubmissionsViewModel(repository, automaticMealSubmission) as T
    }
  }
}

data class FailedMealSubmissionsState(val submissions: List<MealSubmissionEntity> = emptyList())
