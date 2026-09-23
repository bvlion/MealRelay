package net.ambitious.android.mealrelay.submission

import android.net.Uri
import net.ambitious.android.mealrelay.data.database.MealRelayDatabase
import net.ambitious.android.mealrelay.data.photo.PhotoResultEntity
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
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
    database.runInTransaction {
      if (database.photoProcessingDao().hasFoodResult(imageUri, capturedAt)) return@runInTransaction
      val submissionMealId = UUID.randomUUID().toString()
      createdAt = System.currentTimeMillis()
      repository.insert(MealSubmissionEntity(
        mealId = submissionMealId,
        type = MealSubmissionEntity.TYPE_IMAGE,
        imageUri = imageUri,
        text = null,
        occurredAt = occurredAt,
        createdAt = createdAt,
      ))
      database.photoProcessingDao().insertResult(PhotoResultEntity(version, imageUri, capturedAt, true))
      mealId = submissionMealId
    }
    mealId?.let { scheduler.schedule(it, createdAt) }
    return mealId
  }
}

data class MealSubmissionDraft(
  val type: String,
  val imageUri: String?,
  val text: String?,
  val occurredAt: String,
)
