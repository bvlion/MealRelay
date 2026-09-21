package net.ambitious.android.mealrelay

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class MealSubmissionQueue(
  private val context: Context,
  private val dao: PhotoProcessingDao,
) {
  fun enqueueText(text: String, inputAt: Long): String {
    val mealId = UUID.randomUUID().toString()
    dao.insertMealSubmission(
      MealSubmissionEntity(
        mealId = mealId,
        type = MealSubmissionEntity.TYPE_TEXT,
        imageUri = null,
        text = text,
        occurredAt = inputAt,
        createdAt = System.currentTimeMillis(),
      ),
    )
    MealSubmissionWorker.enqueue(context)
    return mealId
  }
}

interface MealSubmissionApi {
  @Multipart
  @POST
  suspend fun submitImage(
    @Url endpoint: String,
    @Part image: MultipartBody.Part,
    @Part("capturedAt") capturedAt: okhttp3.RequestBody,
    @Part("mealId") mealId: okhttp3.RequestBody,
  )

  @POST
  suspend fun submitText(@Url endpoint: String, @Body request: TextMealSubmissionRequest)
}

data class TextMealSubmissionRequest(val text: String, val inputAt: String, val mealId: String)

fun interface MealSubmissionTransport {
  suspend fun submit(submission: MealSubmissionEntity)
}

class RetrofitMealSubmissionTransport(
  private val context: Context,
  private val backendClient: MealRelayBackendClient,
  private val imageEndpoint: String = BuildConfig.MEAL_RELAY_IMAGE_ENDPOINT,
  private val textEndpoint: String = BuildConfig.MEAL_RELAY_TEXT_ENDPOINT,
) : MealSubmissionTransport {
  override suspend fun submit(submission: MealSubmissionEntity) {
    val api = backendClient.authenticatedService(MealSubmissionApi::class.java)
    when (submission.type) {
      MealSubmissionEntity.TYPE_IMAGE -> {
        check(imageEndpoint.isNotBlank()) { "Image meal endpoint is not configured" }
        val imageUri = checkNotNull(submission.imageUri)
        api.submitImage(
          imageEndpoint,
          createImagePart(context.contentResolver, imageUri),
          formatOccurredAt(submission.occurredAt).toRequestBody("text/plain".toMediaType()),
          submission.mealId.toRequestBody("text/plain".toMediaType()),
        )
      }
      MealSubmissionEntity.TYPE_TEXT -> {
        check(textEndpoint.isNotBlank()) { "Text meal endpoint is not configured" }
        api.submitText(
          textEndpoint,
          TextMealSubmissionRequest(
            text = checkNotNull(submission.text),
            inputAt = formatOccurredAt(submission.occurredAt),
            mealId = submission.mealId,
          ),
        )
      }
      else -> error("Meal submission type is invalid")
    }
  }

  private fun createImagePart(contentResolver: ContentResolver, imageUri: String): MultipartBody.Part {
    val uri = Uri.parse(imageUri)
    val mediaType = (contentResolver.getType(uri) ?: "application/octet-stream").toMediaType()
    val hasFileDescriptor = contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    if (!hasFileDescriptor) {
      throw FileNotFoundException(imageUri)
    }
    val requestBody = object : okhttp3.RequestBody() {
      override fun contentType() = mediaType

      override fun writeTo(sink: BufferedSink) {
        contentResolver.openInputStream(uri)?.use { input -> input.copyTo(sink.outputStream()) }
          ?: throw FileNotFoundException(imageUri)
      }
    }
    return MultipartBody.Part.createFormData("image", "meal-image", requestBody)
  }
}

class MealSubmissionManager(
  private val dao: PhotoProcessingDao,
  private val transport: MealSubmissionTransport,
) {
  suspend fun submitAutomatically(submission: MealSubmissionEntity): AutomaticSubmissionResult {
    dao.incrementAutomaticAttemptCount(submission.mealId)
    return try {
      transport.submit(submission)
      dao.deleteMealSubmission(submission.mealId)
      AutomaticSubmissionResult.SUCCEEDED
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      val refreshedSubmission = dao.getMealSubmission(submission.mealId) ?: return AutomaticSubmissionResult.SUCCEEDED
      if (isRetryable(error) && refreshedSubmission.automaticAttemptCount < MAXIMUM_AUTOMATIC_ATTEMPTS) {
        AutomaticSubmissionResult.RETRY
      } else {
        dao.updateMealSubmissionState(submission.mealId, MealSubmissionEntity.STATE_FAILED)
        AutomaticSubmissionResult.FAILED
      }
    }
  }

  suspend fun submitManually(mealId: String): ManualSubmissionResult {
    val submission = dao.getMealSubmission(mealId) ?: return ManualSubmissionResult.NOT_FOUND
    return try {
      transport.submit(submission)
      dao.deleteMealSubmission(mealId)
      ManualSubmissionResult.SUCCEEDED
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      ManualSubmissionResult.FAILED
    }
  }

  private fun isRetryable(error: Exception): Boolean = when (error) {
    is IOException -> true
    is HttpException -> error.code() == 408 || error.code() == 429 || error.code() >= 500
    else -> false
  }

  companion object {
    const val MAXIMUM_AUTOMATIC_ATTEMPTS = 3
  }
}

enum class AutomaticSubmissionResult { SUCCEEDED, RETRY, FAILED }
enum class ManualSubmissionResult { SUCCEEDED, FAILED, NOT_FOUND }

fun formatOccurredAt(occurredAt: Long): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
  Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()),
)
