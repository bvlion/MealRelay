package net.ambitious.android.mealrelay.submission.network

import kotlinx.coroutines.CancellationException
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.MealSubmissionSendResult
import net.ambitious.android.mealrelay.submission.MealSubmissionSender
import net.ambitious.android.mealrelay.submission.MealSubmissionReadiness
import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequestFactory

class TextMealSubmissionSender(
  private val readinessChecker: TextMealSubmissionReadinessChecker,
  private val requestFactory: TextMealSubmissionRequestFactory,
  private val transport: TextMealSubmissionTransport,
  private val failureClassifier: TextMealSubmissionFailureClassifier,
) : MealSubmissionSender {
  override suspend fun readiness(submission: MealSubmissionEntity): MealSubmissionReadiness =
    readinessChecker.check(submission)

  override suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult {
    return try {
      transport.submit(requestFactory.create(submission))
      MealSubmissionSendResult.Succeeded
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      failureClassifier.classify(error)
    }
  }
}
