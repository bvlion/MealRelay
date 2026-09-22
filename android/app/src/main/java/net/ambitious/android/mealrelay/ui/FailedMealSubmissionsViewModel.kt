package net.ambitious.android.mealrelay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.ManualMealSubmission
import net.ambitious.android.mealrelay.submission.MealSubmissionRepository
import javax.inject.Inject

@HiltViewModel
class FailedMealSubmissionsViewModel @Inject constructor(
  private val repository: MealSubmissionRepository,
  private val manualMealSubmission: ManualMealSubmission,
) : ViewModel() {
  var state by mutableStateOf(FailedMealSubmissionsState())
    private set

  fun load() {
    viewModelScope.launch {
      state = FailedMealSubmissionsState(
        submissions = withContext(Dispatchers.IO) {
          repository.failedSubmissions().map(FailedMealSubmission::from)
        },
      )
    }
  }

  fun retry(mealId: String) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { manualMealSubmission.submit(mealId) }
      load()
    }
  }
}

data class FailedMealSubmissionsState(val submissions: List<FailedMealSubmission> = emptyList())

data class FailedMealSubmission(
  val mealId: String,
  val type: FailedMealSubmissionType,
  val content: String?,
  val occurredAt: String,
) {
  companion object {
    fun from(submission: MealSubmissionEntity) = FailedMealSubmission(
      mealId = submission.mealId,
      type = if (submission.type == MealSubmissionEntity.TYPE_IMAGE) {
        FailedMealSubmissionType.Image
      } else {
        FailedMealSubmissionType.Text
      },
      content = submission.text,
      occurredAt = submission.occurredAt,
    )
  }
}

enum class FailedMealSubmissionType { Image, Text }
