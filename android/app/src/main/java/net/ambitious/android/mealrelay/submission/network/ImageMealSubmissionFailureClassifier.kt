package net.ambitious.android.mealrelay.submission.network

import net.ambitious.android.mealrelay.submission.MealSubmissionSendResult
import net.ambitious.android.mealrelay.submission.isRetryableBackendStatus
import retrofit2.HttpException
import java.io.IOException

class ImageMealSubmissionFailureClassifier {
  fun classify(error: Exception): MealSubmissionSendResult = when (error) {
    is HttpException -> when {
      isRetryableBackendStatus(error.code()) -> MealSubmissionSendResult.RetryableFailure(error)
      error.code() in 400..499 && error.code() != 401 ->
        MealSubmissionSendResult.PermanentFailure(error, isManualRetryAvailable = false)
      else -> MealSubmissionSendResult.PermanentFailure(error)
    }
    is IOException -> MealSubmissionSendResult.RetryableFailure(error)
    else -> MealSubmissionSendResult.PermanentFailure(error)
  }
}
