package net.ambitious.android.mealrelay.submission

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MealSubmissionWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted parameters: WorkerParameters,
  private val automaticMealSubmissionProcessor: AutomaticMealSubmissionProcessor,
  private val mealSubmissionFailureNotifier: MealSubmissionFailureNotifier,
  private val mealSubmissionWorkScheduler: MealSubmissionWorkScheduler,
) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    val mealId = inputData.getString(MEAL_ID) ?: return Result.failure()
    return when (val result = automaticMealSubmissionProcessor.submit(mealId)) {
      AutomaticMealSubmissionResult.Completed -> Result.success()
      AutomaticMealSubmissionResult.AwaitingAuthorization -> Result.success()
      AutomaticMealSubmissionResult.Failed -> {
        mealSubmissionFailureNotifier.notifyFailure()
        Result.success()
      }
      is AutomaticMealSubmissionResult.RetryAt -> {
        mealSubmissionWorkScheduler.schedule(mealId, result.timeMillis)
        Result.success()
      }
    }
  }

  companion object {
    private const val MEAL_ID = "meal_id"

    fun inputData(mealId: String): Data = Data.Builder().putString(MEAL_ID, mealId).build()
  }
}
