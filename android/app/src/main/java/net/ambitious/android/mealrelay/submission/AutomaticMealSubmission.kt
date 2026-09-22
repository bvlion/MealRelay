package net.ambitious.android.mealrelay.submission

import kotlinx.coroutines.CancellationException
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import javax.inject.Inject

class AutomaticMealSubmissionProcessor @Inject constructor(
  private val repository: MealSubmissionRepository,
  private val sender: MealSubmissionSender,
  private val clock: SubmissionClock,
) {
  suspend fun submit(mealId: String): AutomaticMealSubmissionResult {
    val initialSubmission = repository.get(mealId) ?: return AutomaticMealSubmissionResult.Completed
    if (initialSubmission.state == MealSubmissionEntity.STATE_FAILED) return AutomaticMealSubmissionResult.Completed
    if (initialSubmission.state == MealSubmissionEntity.STATE_SENDING) {
      repository.recoverInterruptedAutomaticAttempt(mealId)
    }
    val submission = repository.get(mealId) ?: return AutomaticMealSubmissionResult.Completed
    if (submission.state != MealSubmissionEntity.STATE_PENDING) return AutomaticMealSubmissionResult.Completed
    if (submission.automaticAttemptCount >= MAXIMUM_AUTOMATIC_ATTEMPTS) {
      repository.recordAutomaticFailure(mealId, submission.automaticAttemptCount)
      return AutomaticMealSubmissionResult.Failed
    }
    if (submission.nextAutomaticAttemptAt?.let { it > clock.now() } == true) {
      return AutomaticMealSubmissionResult.RetryAt(checkNotNull(submission.nextAutomaticAttemptAt))
    }
    when (sender.readiness(submission)) {
      MealSubmissionReadiness.AwaitingAuthorization -> return AutomaticMealSubmissionResult.AwaitingAuthorization
      MealSubmissionReadiness.Unavailable -> {
        repository.recordAutomaticFailure(mealId, submission.automaticAttemptCount)
        return AutomaticMealSubmissionResult.Failed
      }
      MealSubmissionReadiness.Ready -> Unit
    }
    val sendingSubmission = repository.beginAutomaticAttempt(mealId, MAXIMUM_AUTOMATIC_ATTEMPTS)
      ?: return AutomaticMealSubmissionResult.Completed
    val result = try {
      sender.send(sendingSubmission)
    } catch (error: CancellationException) {
      throw error
    }
    return when (result) {
      MealSubmissionSendResult.Succeeded -> {
        repository.delete(mealId)
        AutomaticMealSubmissionResult.Completed
      }
      is MealSubmissionSendResult.RetryableFailure -> recordFailure(sendingSubmission, true)
      is MealSubmissionSendResult.PermanentFailure -> recordFailure(sendingSubmission, false)
    }
  }

  private fun recordFailure(
    submission: MealSubmissionEntity,
    isRetryable: Boolean,
  ): AutomaticMealSubmissionResult {
    val attemptCount = submission.automaticAttemptCount
    if (!isRetryable || attemptCount == MAXIMUM_AUTOMATIC_ATTEMPTS) {
      repository.recordAutomaticFailure(submission.mealId, attemptCount)
      return AutomaticMealSubmissionResult.Failed
    }
    val nextAttemptAt = clock.now() + when (attemptCount) {
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
  data object AwaitingAuthorization : AutomaticMealSubmissionResult
  data object Failed : AutomaticMealSubmissionResult
  data class RetryAt(val timeMillis: Long) : AutomaticMealSubmissionResult
}
