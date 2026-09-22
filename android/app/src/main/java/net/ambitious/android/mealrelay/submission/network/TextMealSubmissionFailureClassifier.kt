package net.ambitious.android.mealrelay.submission.network

import net.ambitious.android.mealrelay.submission.MealSubmissionSendResult
import net.ambitious.android.mealrelay.submission.isRetryableBackendStatus
import retrofit2.HttpException
import java.io.IOException

class TextMealSubmissionFailureClassifier {
  fun classify(error: Exception): MealSubmissionSendResult = when (error) {
    is HttpException -> if (isRetryableBackendStatus(error.code())) {
      MealSubmissionSendResult.RetryableFailure(error)
    } else {
      MealSubmissionSendResult.PermanentFailure(error)
    }
    is IOException -> MealSubmissionSendResult.RetryableFailure(error)
    else -> MealSubmissionSendResult.PermanentFailure(error)
  }
}
