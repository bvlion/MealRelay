package net.ambitious.android.mealrelay

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class MealSubmissionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    val database = PhotoProcessingDatabase.get(applicationContext)
    val manager = MealSubmissionManager(
      database.photoProcessingDao(),
      RetrofitMealSubmissionTransport(
        applicationContext,
        MealRelayBackendClient(MealRelayTokenStore(applicationContext)::read),
      ),
    )
    var shouldRetry = false
    for (submission in database.photoProcessingDao().getMealSubmissionsWithState(MealSubmissionEntity.STATE_PENDING)) {
      when (manager.submitAutomatically(submission)) {
        AutomaticSubmissionResult.RETRY -> shouldRetry = true
        AutomaticSubmissionResult.FAILED -> MealSubmissionFailureNotifier.notify(applicationContext)
        AutomaticSubmissionResult.SUCCEEDED -> Unit
      }
    }
    return if (shouldRetry) Result.retry() else Result.success()
  }

  companion object {
    fun enqueue(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        "meal_submission",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<MealSubmissionWorker>()
          .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
          .build(),
      )
    }
  }
}
