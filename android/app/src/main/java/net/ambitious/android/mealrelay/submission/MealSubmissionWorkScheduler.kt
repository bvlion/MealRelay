package net.ambitious.android.mealrelay.submission

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
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

  fun cancel(mealId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(workName(mealId))
  }

  suspend fun resumePendingSubmissions() = withContext(Dispatchers.IO) {
    repository.pendingSubmissions().forEach { submission ->
      var schedulePolicy = ExistingWorkPolicy.KEEP
      var submissionAt = submission.nextAutomaticAttemptAt ?: System.currentTimeMillis()
      if (submission.nextAutomaticAttemptAt == null &&
        submission.type == MealSubmissionEntity.TYPE_IMAGE &&
        submission.automaticAttemptCount == 0
      ) {
        val latestPhotoCaptureAt = submission.imagePayload
          ?.let { ImageMealSubmissionPayload.decode(it) }
          ?.photos
          ?.mapNotNull { it.capturedAt }
          ?.maxOrNull()
        if (latestPhotoCaptureAt != null) {
          submissionAt = latestPhotoCaptureAt + MealSubmissionQueue.FOOD_MEAL_WINDOW_MILLIS
          if (repository.restorePendingImageSubmissionTime(submission.mealId, submissionAt)) {
            schedulePolicy = ExistingWorkPolicy.REPLACE
          }
        }
      }
      schedule(submission.mealId, submissionAt, schedulePolicy)
    }
  }

  companion object {
    fun workName(mealId: String) = "meal_submission_$mealId"
  }
}
