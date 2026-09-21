package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.MealSubmissionEntity

fun interface MealSubmissionSender {
  suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult
}

sealed interface MealSubmissionSendResult {
  data object Succeeded : MealSubmissionSendResult
  data object AuthenticationRequired : MealSubmissionSendResult
  data object Deferred : MealSubmissionSendResult
  data class RetryableFailure(val cause: Throwable) : MealSubmissionSendResult
  data class PermanentFailure(val cause: Throwable) : MealSubmissionSendResult
}
