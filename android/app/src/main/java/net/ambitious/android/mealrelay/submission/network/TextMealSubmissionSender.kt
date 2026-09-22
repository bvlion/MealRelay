package net.ambitious.android.mealrelay.submission.network

import kotlinx.coroutines.CancellationException
import net.ambitious.android.mealrelay.BuildConfig
import net.ambitious.android.mealrelay.MealRelayBackendClient
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.MealSubmissionSendResult
import net.ambitious.android.mealrelay.submission.MealSubmissionSender
import net.ambitious.android.mealrelay.submission.MealSubmissionReadiness
import net.ambitious.android.mealrelay.submission.isRetryableBackendStatus
import net.ambitious.android.mealrelay.submission.isRetryableNetworkException
import net.ambitious.android.mealrelay.submission.network.api.TextMealSubmissionApi
import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequest
import retrofit2.HttpException
import java.io.IOException

class TextMealSubmissionSender(
  private val tokenStore: MealRelayTokenStore,
  private val backendClient: MealRelayBackendClient,
  private val endpoint: String = BuildConfig.MEAL_RELAY_TEXT_ENDPOINT,
) : MealSubmissionSender {
  override suspend fun readiness(submission: MealSubmissionEntity): MealSubmissionReadiness = when {
    tokenStore.read() == null -> MealSubmissionReadiness.AwaitingAuthorization
    submission.type != MealSubmissionEntity.TYPE_TEXT || endpoint.isBlank() -> MealSubmissionReadiness.Unavailable
    else -> MealSubmissionReadiness.Ready
  }

  override suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult {
    return try {
      backendClient.authenticatedService(TextMealSubmissionApi::class.java).submit(
        endpoint,
        TextMealSubmissionRequest(
          text = checkNotNull(submission.text),
          inputAt = submission.occurredAt,
          mealId = submission.mealId,
        ),
      )
      MealSubmissionSendResult.Succeeded
    } catch (error: CancellationException) {
      throw error
    } catch (error: HttpException) {
      if (isRetryableBackendStatus(error.code())) {
        MealSubmissionSendResult.RetryableFailure(error)
      } else {
        MealSubmissionSendResult.PermanentFailure(error)
      }
    } catch (error: IOException) {
      if (isRetryableNetworkException(error)) {
        MealSubmissionSendResult.RetryableFailure(error)
      } else {
        MealSubmissionSendResult.PermanentFailure(error)
      }
    } catch (error: Exception) {
      MealSubmissionSendResult.PermanentFailure(error)
    }
  }
}
