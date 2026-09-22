package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity

interface MealSubmissionSender {
  suspend fun readiness(submission: MealSubmissionEntity): MealSubmissionReadiness
  suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult
}

enum class MealSubmissionReadiness { Ready, AwaitingAuthorization, Unavailable }

sealed interface MealSubmissionSendResult {
  data object Succeeded : MealSubmissionSendResult
  data class RetryableFailure(val cause: Throwable) : MealSubmissionSendResult
  data class PermanentFailure(val cause: Throwable) : MealSubmissionSendResult
}
