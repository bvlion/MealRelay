package net.ambitious.android.mealrelay.submission

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import net.ambitious.android.mealrelay.data.database.MealRelayDatabase
import net.ambitious.android.mealrelay.data.photo.PhotoResultEntity
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import java.util.UUID
import javax.inject.Inject

class MealSubmissionQueue @Inject constructor(
  private val repository: MealSubmissionRepository,
  private val scheduler: MealSubmissionWorkScheduler,
  private val database: MealRelayDatabase,
  private val foodPhotoMealGrouping: FoodPhotoMealGrouping,
) {
  fun enqueue(draft: MealSubmissionDraft): String {
    val mealId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    repository.insert(MealSubmissionEntity(
      mealId = mealId,
      type = draft.type,
      imagePayload = draft.imageUri,
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
    var runAt = 0L
    val removedMealIds = mutableListOf<String>()
    database.runInTransaction {
      if (database.photoProcessingDao().hasFoodResult(imageUri, capturedAt)) return@runInTransaction
      val pendingSubmissions = repository.pendingUnattemptedImageSubmissions()
      val photo = ImageMealSubmissionPayload.Photo(imageUri, capturedAt)
      val group = foodPhotoMealGrouping.group(pendingSubmissions, photo)
        .first { photo in it.photos }
      val photos = group.photos.sortedBy { checkNotNull(it.capturedAt) }
      val firstPhoto = photos.first()
      val firstPhotoSubmission = group.submissions.firstOrNull { submission ->
        ImageMealSubmissionPayload.decode(checkNotNull(submission.imagePayload)).photos.contains(firstPhoto)
      }
      val canonicalSubmission = firstPhotoSubmission ?: group.submissions.minByOrNull { it.createdAt }
      val groupRunAt = checkNotNull(photos.last().capturedAt) + FOOD_MEAL_WINDOW_MILLIS
      val payload = ImageMealSubmissionPayload(photos).encode()
      val firstOccurredAt = firstPhotoSubmission?.occurredAt ?: occurredAt

      if (canonicalSubmission == null) {
        val latestCreatedAt = pendingSubmissions.maxOfOrNull { it.createdAt } ?: 0L
        val createdAt = maxOf(System.currentTimeMillis(), latestCreatedAt + 1)
        val submissionMealId = UUID.randomUUID().toString()
        repository.insert(MealSubmissionEntity(
          mealId = submissionMealId,
          type = MealSubmissionEntity.TYPE_IMAGE,
          imagePayload = payload,
          text = null,
          occurredAt = firstOccurredAt,
          nextAutomaticAttemptAt = groupRunAt,
          createdAt = createdAt,
        ))
        mealId = submissionMealId
      } else {
        check(repository.updatePendingImagePayload(
          canonicalSubmission.mealId,
          payload,
          firstOccurredAt,
          groupRunAt,
        ))
        group.submissions.filter { it.mealId != canonicalSubmission.mealId }.forEach { submission ->
          repository.delete(submission.mealId)
          removedMealIds.add(submission.mealId)
        }
        mealId = canonicalSubmission.mealId
      }
      runAt = groupRunAt
      database.photoProcessingDao().insertResult(PhotoResultEntity(version, imageUri, capturedAt, true))
    }
    removedMealIds.forEach(scheduler::cancel)
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
