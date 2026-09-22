package net.ambitious.android.mealrelay.submission

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MealSubmissionWorkScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repository: MealSubmissionRepository,
) {
  fun schedule(mealId: String, runAt: Long, policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE) {
    val delayMillis = (runAt - System.currentTimeMillis()).coerceAtLeast(0)
    WorkManager.getInstance(context).enqueueUniqueWork(
      workName(mealId),
      policy,
      OneTimeWorkRequestBuilder<MealSubmissionWorker>()
        .setInputData(MealSubmissionWorker.inputData(mealId))
        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build(),
    )
  }

  fun resumePendingSubmissions() {
    repository.pendingSubmissions().forEach { submission ->
      schedule(submission.mealId, submission.nextAutomaticAttemptAt ?: System.currentTimeMillis(), ExistingWorkPolicy.REPLACE)
    }
  }

  companion object {
    fun workName(mealId: String) = "meal_submission_$mealId"
  }
}
