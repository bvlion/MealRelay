package net.ambitious.android.mealrelay.submission

import kotlinx.coroutines.CancellationException
import net.ambitious.android.mealrelay.data.MealSubmissionEntity

class AutomaticMealSubmission(
  private val repository: MealSubmissionRepository,
  private val sender: MealSubmissionSender,
  private val clock: () -> Long,
) {
  suspend fun submit(mealId: String): AutomaticMealSubmissionResult {
    val submission = repository.get(mealId) ?: return AutomaticMealSubmissionResult.Completed
    if (submission.state != MealSubmissionEntity.STATE_PENDING) return AutomaticMealSubmissionResult.Completed
    if (submission.nextAutomaticAttemptAt?.let { it > clock() } == true) {
      return AutomaticMealSubmissionResult.RetryAt(checkNotNull(submission.nextAutomaticAttemptAt))
    }
    val result = try {
      sender.send(submission)
    } catch (error: CancellationException) {
      throw error
    }
    return when (result) {
      MealSubmissionSendResult.Succeeded -> {
        repository.delete(mealId)
        AutomaticMealSubmissionResult.Completed
      }
      MealSubmissionSendResult.AuthenticationRequired,
      MealSubmissionSendResult.Deferred -> AutomaticMealSubmissionResult.Deferred
      is MealSubmissionSendResult.RetryableFailure -> recordFailure(submission, true)
      is MealSubmissionSendResult.PermanentFailure -> recordFailure(submission, false)
    }
  }

  suspend fun submitManually(mealId: String): ManualMealSubmissionResult {
    val submission = repository.get(mealId) ?: return ManualMealSubmissionResult.NotFound
    return when (val result = sender.send(submission)) {
      MealSubmissionSendResult.Succeeded -> {
        repository.delete(mealId)
        ManualMealSubmissionResult.Succeeded
      }
      else -> ManualMealSubmissionResult.Failed
    }
  }

  private fun recordFailure(
    submission: MealSubmissionEntity,
    isRetryable: Boolean,
  ): AutomaticMealSubmissionResult {
    val attemptCount = submission.automaticAttemptCount + 1
    if (!isRetryable || attemptCount == MAXIMUM_AUTOMATIC_ATTEMPTS) {
      repository.recordAutomaticFailure(submission.mealId, attemptCount)
      return AutomaticMealSubmissionResult.Failed
    }
    val nextAttemptAt = clock() + when (attemptCount) {
      1 -> FIRST_RETRY_DELAY_MILLIS
      else -> SECOND_RETRY_DELAY_MILLIS
    }
    repository.recordAutomaticRetry(submission.mealId, attemptCount, nextAttemptAt)
    return AutomaticMealSubmissionResult.RetryAt(nextAttemptAt)
  }

  companion object {
    const val MAXIMUM_AUTOMATIC_ATTEMPTS = 3
    const val FIRST_RETRY_DELAY_MILLIS = 5 * 60 * 1000L
    const val SECOND_RETRY_DELAY_MILLIS = 10 * 60 * 1000L
  }
}

sealed interface AutomaticMealSubmissionResult {
  data object Completed : AutomaticMealSubmissionResult
  data object Deferred : AutomaticMealSubmissionResult
  data object Failed : AutomaticMealSubmissionResult
  data class RetryAt(val timeMillis: Long) : AutomaticMealSubmissionResult
}

enum class ManualMealSubmissionResult { Succeeded, Failed, NotFound }
