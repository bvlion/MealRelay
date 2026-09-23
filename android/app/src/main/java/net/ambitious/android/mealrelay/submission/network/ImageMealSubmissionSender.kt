package net.ambitious.android.mealrelay.submission.network

import kotlinx.coroutines.CancellationException
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.MealSubmissionReadiness
import net.ambitious.android.mealrelay.submission.MealSubmissionSendResult
import net.ambitious.android.mealrelay.submission.MealSubmissionSender
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionRequestFactory

class ImageMealSubmissionSender(
  private val readinessChecker: ImageMealSubmissionReadinessChecker,
  private val imageSource: ImageMealSubmissionImageSource,
  private val requestFactory: ImageMealSubmissionRequestFactory,
  private val transport: ImageMealSubmissionTransport,
  private val failureClassifier: ImageMealSubmissionFailureClassifier,
) : MealSubmissionSender {
  override suspend fun readiness(submission: MealSubmissionEntity): MealSubmissionReadiness =
    readinessChecker.check(submission)

  override suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult = try {
    transport.submit(requestFactory.create(submission, imageSource.read(checkNotNull(submission.imageUri))))
    MealSubmissionSendResult.Succeeded
  } catch (error: CancellationException) {
    throw error
  } catch (error: Exception) {
    failureClassifier.classify(error)
  }
}
