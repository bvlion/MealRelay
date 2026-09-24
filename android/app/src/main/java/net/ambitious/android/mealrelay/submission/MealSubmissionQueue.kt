package net.ambitious.android.mealrelay.submission

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import net.ambitious.android.mealrelay.data.database.MealRelayDatabase
import net.ambitious.android.mealrelay.data.photo.PhotoResultEntity
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class MealSubmissionQueue @Inject constructor(
  private val repository: MealSubmissionRepository,
  private val scheduler: MealSubmissionWorkScheduler,
  private val database: MealRelayDatabase,
) {
  fun enqueue(draft: MealSubmissionDraft): String {
    val mealId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    repository.insert(MealSubmissionEntity(
      mealId = mealId,
      type = draft.type,
      imageUri = draft.imageUri,
      text = draft.text,
      occurredAt = draft.occurredAt,
      createdAt = now,
    ))
    scheduler.schedule(mealId, now)
    return mealId
  }

  fun enqueueFoodPhoto(uri: Uri, capturedAt: Long, version: String, occurredAt: String): String? {
    val imageUri = uri.toString()
    var mealId: String? = null
    var createdAt = 0L
    var runAt = 0L
    database.runInTransaction {
      if (database.photoProcessingDao().hasFoodResult(imageUri, capturedAt)) return@runInTransaction
      val latestSubmission = repository.latestPendingImage()
      val latestImage = latestSubmission?.imageUri?.let { previousImages ->
        if (previousImages.startsWith("[")) {
          val imageArray = JSONArray(previousImages)
          (0 until imageArray.length()).map { index ->
            val image = imageArray.getJSONObject(index)
            image.getString("uri") to image.getLong("capturedAt")
          }
        } else {
          listOf(previousImages to Long.MIN_VALUE)
        }
      }
      val lastCapturedAt = latestImage?.lastOrNull()?.second
      var images = listOf(imageUri to capturedAt)
      if (latestSubmission != null && lastCapturedAt != null && capturedAt >= lastCapturedAt &&
        capturedAt - lastCapturedAt < FOOD_MEAL_WINDOW_MILLIS
      ) {
        images = latestImage + (imageUri to capturedAt)
        val imageArray = JSONArray().apply {
          images.forEach { (photoUri, photoCapturedAt) ->
            put(JSONObject().put("uri", photoUri).put("capturedAt", photoCapturedAt))
          }
        }
        if (repository.updatePendingImageUris(latestSubmission.mealId, imageArray.toString())) {
          mealId = latestSubmission.mealId
          createdAt = latestSubmission.createdAt
          runAt = capturedAt + FOOD_MEAL_WINDOW_MILLIS
        }
      }
      if (mealId == null) {
        val submissionMealId = UUID.randomUUID().toString()
        createdAt = maxOf(System.currentTimeMillis(), (latestSubmission?.createdAt ?: 0L) + 1)
        runAt = capturedAt + FOOD_MEAL_WINDOW_MILLIS
        val imageArray = JSONArray().apply {
          images.forEach { (photoUri, photoCapturedAt) ->
            put(JSONObject().put("uri", photoUri).put("capturedAt", photoCapturedAt))
          }
        }
        repository.insert(MealSubmissionEntity(
          mealId = submissionMealId,
          type = MealSubmissionEntity.TYPE_IMAGE,
          imageUri = imageArray.toString(),
          text = null,
          occurredAt = occurredAt,
          createdAt = createdAt,
        ))
        mealId = submissionMealId
      }
      database.photoProcessingDao().insertResult(PhotoResultEntity(version, imageUri, capturedAt, true))
    }
    mealId?.let { scheduler.schedule(it, runAt, ExistingWorkPolicy.REPLACE) }
    return mealId
  }

  companion object {
    const val FOOD_MEAL_WINDOW_MILLIS = 15 * 60 * 1000L
  }
}

data class MealSubmissionDraft(
  val type: String,
  val imageUri: String?,
  val text: String?,
  val occurredAt: String,
)
