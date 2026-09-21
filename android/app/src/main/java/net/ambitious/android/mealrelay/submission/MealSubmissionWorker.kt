package net.ambitious.android.mealrelay.submission

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import net.ambitious.android.mealrelay.MealRelayApplication

class MealSubmissionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    val mealId = inputData.getString(MEAL_ID) ?: return Result.failure()
    val application = applicationContext as MealRelayApplication
    return when (val result = application.automaticMealSubmission.submit(mealId)) {
      AutomaticMealSubmissionResult.Completed -> Result.success()
      AutomaticMealSubmissionResult.Deferred -> Result.retry()
      AutomaticMealSubmissionResult.Failed -> {
        application.mealSubmissionFailureNotifier.notifyFailure()
        Result.success()
      }
      is AutomaticMealSubmissionResult.RetryAt -> {
        application.mealSubmissionWorkScheduler.schedule(mealId, result.timeMillis)
        Result.success()
      }
    }
  }

  companion object {
    private const val MEAL_ID = "meal_id"

    fun inputData(mealId: String): Data = Data.Builder().putString(MEAL_ID, mealId).build()
  }
}
