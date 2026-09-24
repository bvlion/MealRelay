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
    return enqueueFoodPhotos(listOf(FoodPhotoSubmissionDraft(
      imageUri = uri.toString(),
      capturedAt = capturedAt,
      version = version,
      occurredAt = occurredAt,
    ))).firstOrNull()
  }

  fun enqueueFoodPhotos(photos: List<FoodPhotoSubmissionDraft>): List<String> {
    if (photos.isEmpty()) return emptyList()
    val mealIds = linkedSetOf<String>()
    val removedMealIds = mutableListOf<String>()
    database.runInTransaction {
      photos.sortedBy { it.capturedAt }.forEach { photo ->
        enqueueFoodPhoto(photo, mealIds, removedMealIds)
      }
    }
    removedMealIds.forEach(scheduler::cancel)
    return mealIds.mapNotNull { mealId ->
      val submission = repository.get(mealId) ?: return@mapNotNull null
      scheduler.schedule(
        mealId,
        checkNotNull(submission.nextAutomaticAttemptAt),
        ExistingWorkPolicy.REPLACE,
      )
      mealId
    }
  }

  private fun enqueueFoodPhoto(
    photo: FoodPhotoSubmissionDraft,
    mealIds: MutableSet<String>,
    removedMealIds: MutableList<String>,
  ) {
    if (database.photoProcessingDao().hasFoodResult(photo.imageUri, photo.capturedAt)) return
    val pendingSubmissions = repository.pendingUnattemptedImageSubmissions()
    val newPhoto = ImageMealSubmissionPayload.Photo(photo.imageUri, photo.capturedAt)
    val group = foodPhotoMealGrouping.group(pendingSubmissions, newPhoto)
      .first { newPhoto in it.photos }
    val groupedPhotos = group.photos.sortedBy { checkNotNull(it.capturedAt) }
    val firstPhoto = groupedPhotos.first()
    val firstPhotoSubmission = group.submissions.firstOrNull { submission ->
      ImageMealSubmissionPayload.decode(checkNotNull(submission.imagePayload)).photos.contains(firstPhoto)
    }
    val canonicalSubmission = firstPhotoSubmission ?: group.submissions.minByOrNull { it.createdAt }
    val groupRunAt = checkNotNull(groupedPhotos.last().capturedAt) + FOOD_MEAL_WINDOW_MILLIS
    val payload = ImageMealSubmissionPayload(groupedPhotos).encode()
    val firstOccurredAt = firstPhotoSubmission?.occurredAt ?: photo.occurredAt

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
      mealIds.add(submissionMealId)
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
      mealIds.add(canonicalSubmission.mealId)
    }
    database.photoProcessingDao().insertResult(
      PhotoResultEntity(photo.version, photo.imageUri, photo.capturedAt, true),
    )
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

data class FoodPhotoSubmissionDraft(
  val imageUri: String,
  val capturedAt: Long,
  val version: String,
  val occurredAt: String,
)
