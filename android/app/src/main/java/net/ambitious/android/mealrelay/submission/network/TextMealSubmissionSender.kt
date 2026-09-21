package net.ambitious.android.mealrelay.submission.network

import kotlinx.coroutines.CancellationException
import net.ambitious.android.mealrelay.BuildConfig
import net.ambitious.android.mealrelay.MealRelayBackendClient
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.data.MealSubmissionEntity
import net.ambitious.android.mealrelay.submission.MealSubmissionSendResult
import net.ambitious.android.mealrelay.submission.MealSubmissionSender
import net.ambitious.android.mealrelay.submission.isRetryableBackendStatus
import net.ambitious.android.mealrelay.submission.isRetryableNetworkException
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.io.IOException

class TextMealSubmissionSender(
  private val tokenStore: MealRelayTokenStore,
  private val backendClient: MealRelayBackendClient,
  private val endpoint: String = BuildConfig.MEAL_RELAY_TEXT_ENDPOINT,
) : MealSubmissionSender {
  override suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult {
    if (tokenStore.read() == null) return MealSubmissionSendResult.AuthenticationRequired
    if (submission.type != MealSubmissionEntity.TYPE_TEXT) return MealSubmissionSendResult.Deferred
    if (endpoint.isBlank()) return MealSubmissionSendResult.Deferred
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

private interface TextMealSubmissionApi {
  @POST
  suspend fun submit(@Url endpoint: String, @Body request: TextMealSubmissionRequest)
}

private data class TextMealSubmissionRequest(val text: String, val inputAt: String, val mealId: String)
