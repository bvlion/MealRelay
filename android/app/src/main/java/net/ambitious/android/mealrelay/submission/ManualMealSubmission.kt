package net.ambitious.android.mealrelay.submission

import javax.inject.Inject

class ManualMealSubmission @Inject constructor(
  private val repository: MealSubmissionRepository,
  private val sender: MealSubmissionSender,
) {
  suspend fun submit(mealId: String): ManualMealSubmissionResult {
    val submission = repository.get(mealId) ?: return ManualMealSubmissionResult.NotFound
    if (sender.readiness(submission) != MealSubmissionReadiness.Ready) return ManualMealSubmissionResult.Failed
    return when (sender.send(submission)) {
      MealSubmissionSendResult.Succeeded -> {
        repository.delete(mealId)
        ManualMealSubmissionResult.Succeeded
      }
      else -> ManualMealSubmissionResult.Failed
    }
  }
}

enum class ManualMealSubmissionResult { Succeeded, Failed, NotFound }
