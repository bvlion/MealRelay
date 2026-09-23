package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import javax.inject.Inject

class AutomaticMealSubmissionProcessor @Inject constructor(
  private val repository: MealSubmissionRepository,
  private val sender: MealSubmissionSender,
  private val clock: SubmissionClock,
) {
  suspend fun submit(mealId: String): AutomaticMealSubmissionResult {
    val submission = loadPendingSubmission(mealId) ?: return AutomaticMealSubmissionResult.Completed
    retryResult(submission)?.let { return it }
    when (sender.readiness(submission)) {
      MealSubmissionReadiness.AwaitingAuthorization -> return AutomaticMealSubmissionResult.AwaitingAuthorization
      MealSubmissionReadiness.Unavailable -> return failWithoutSending(submission)
      MealSubmissionReadiness.Ready -> Unit
    }
    return sendAutomatically(submission)
  }

  private fun loadPendingSubmission(mealId: String): MealSubmissionEntity? {
    val initialSubmission = repository.get(mealId) ?: return null
    if (initialSubmission.state == MealSubmissionEntity.STATE_SENDING) {
      repository.recoverInterruptedAutomaticAttempt(mealId)
    }
    return repository.get(mealId)?.takeIf { it.state == MealSubmissionEntity.STATE_PENDING }
  }

  private fun retryResult(submission: MealSubmissionEntity): AutomaticMealSubmissionResult? {
    if (submission.automaticAttemptCount >= MAXIMUM_AUTOMATIC_ATTEMPTS) {
      return failWithoutSending(submission)
    }
    return submission.nextAutomaticAttemptAt
      ?.takeIf { it > clock.now() }
      ?.let(AutomaticMealSubmissionResult::RetryAt)
  }

  private suspend fun sendAutomatically(submission: MealSubmissionEntity): AutomaticMealSubmissionResult {
    val nextRetryAt = clock.now() + retryDelayFor(submission.automaticAttemptCount + 1)
    val sendingSubmission = repository.beginAutomaticAttempt(
      submission.mealId,
      MAXIMUM_AUTOMATIC_ATTEMPTS,
      nextRetryAt,
    ) ?: return AutomaticMealSubmissionResult.Completed
    return persistSendResult(sendingSubmission, sender.send(sendingSubmission))
  }

  private fun persistSendResult(
    submission: MealSubmissionEntity,
    result: MealSubmissionSendResult,
  ): AutomaticMealSubmissionResult = when (result) {
    MealSubmissionSendResult.Succeeded -> {
      repository.delete(submission.mealId)
      AutomaticMealSubmissionResult.Completed
    }
    is MealSubmissionSendResult.RetryableFailure -> recordFailure(submission, true)
    is MealSubmissionSendResult.PermanentFailure -> recordFailure(
      submission,
      false,
      result.isManualRetryAvailable,
    )
  }

  private fun failWithoutSending(submission: MealSubmissionEntity): AutomaticMealSubmissionResult {
    repository.recordAutomaticFailure(submission.mealId, submission.automaticAttemptCount)
    return AutomaticMealSubmissionResult.Failed
  }

  private fun recordFailure(
    submission: MealSubmissionEntity,
    isRetryable: Boolean,
    isManualRetryAvailable: Boolean = true,
  ): AutomaticMealSubmissionResult {
    val attemptCount = submission.automaticAttemptCount
    if (!isRetryable || attemptCount == MAXIMUM_AUTOMATIC_ATTEMPTS) {
      repository.recordAutomaticFailure(submission.mealId, attemptCount, isManualRetryAvailable)
      return AutomaticMealSubmissionResult.Failed
    }
    val nextAttemptAt = clock.now() + retryDelayFor(attemptCount)
    repository.recordAutomaticRetry(submission.mealId, attemptCount, nextAttemptAt)
    return AutomaticMealSubmissionResult.RetryAt(nextAttemptAt)
  }

  private fun retryDelayFor(attemptCount: Int): Long = when (attemptCount) {
    1 -> FIRST_RETRY_DELAY_MILLIS
    else -> SECOND_RETRY_DELAY_MILLIS
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
