package net.ambitious.android.mealrelay

import android.app.Application
import net.ambitious.android.mealrelay.data.MealRelayDatabase
import net.ambitious.android.mealrelay.submission.AutomaticMealSubmission
import net.ambitious.android.mealrelay.submission.MealSubmissionFailureNotifier
import net.ambitious.android.mealrelay.submission.MealSubmissionRepository
import net.ambitious.android.mealrelay.submission.MealSubmissionQueue
import net.ambitious.android.mealrelay.submission.MealSubmissionWorkScheduler
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionSender

class MealRelayApplication : Application() {
  private val tokenStore by lazy { MealRelayTokenStore(this) }
  val mealSubmissionRepository by lazy {
    MealSubmissionRepository(MealRelayDatabase.get(this).mealSubmissionDao())
  }
  val mealSubmissionWorkScheduler by lazy {
    MealSubmissionWorkScheduler(this, mealSubmissionRepository)
  }
  val automaticMealSubmission by lazy {
    AutomaticMealSubmission(
      mealSubmissionRepository,
      TextMealSubmissionSender(
        tokenStore,
        MealRelayBackendClient(tokenStore::read),
      ),
      System::currentTimeMillis,
    )
  }
  val mealSubmissionQueue by lazy {
    MealSubmissionQueue(mealSubmissionRepository, mealSubmissionWorkScheduler, System::currentTimeMillis)
  }
  val mealSubmissionFailureNotifier by lazy { MealSubmissionFailureNotifier(this) }

  fun resumePendingMealSubmissions() {
    mealSubmissionWorkScheduler.resumePendingSubmissions()
  }
}
