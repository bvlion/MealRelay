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
    val changes = FoodPhotoQueueChanges()
    database.runInTransaction {
      photos.sortedBy(FoodPhotoSubmissionDraft::capturedAt).forEach { photo ->
        persistFoodPhoto(photo)?.let { update ->
          changes.mealIdsToSchedule.add(update.mealId)
          changes.mealIdsToCancel.addAll(update.removedMealIds)
        }
      }
    }
    changes.mealIdsToCancel.forEach(scheduler::cancel)
    return changes.mealIdsToSchedule.mapNotNull { mealId ->
      repository.get(mealId)?.let { submission ->
        scheduler.schedule(
          mealId,
          checkNotNull(submission.nextAutomaticAttemptAt),
          ExistingWorkPolicy.REPLACE,
        )
        mealId
      }
    }
  }

  private fun persistFoodPhoto(photo: FoodPhotoSubmissionDraft): FoodPhotoQueueUpdate? {
    if (database.photoProcessingDao().hasFoodResult(photo.imageUri, photo.capturedAt)) return null
    val pendingSubmissions = repository.pendingUnattemptedImageSubmissions()
    val newPhoto = ImageMealSubmissionPayload.Photo(photo.imageUri, photo.capturedAt)
    val group = foodPhotoMealGrouping.group(pendingSubmissions, newPhoto)
      .first { newPhoto in it.photos }
    val groupedPhotos = group.photos.sortedBy { checkNotNull(it.capturedAt) }
    val firstPhoto = groupedPhotos.first()
    val scheduledAt = checkNotNull(groupedPhotos.last().capturedAt) + FOOD_MEAL_WINDOW_MILLIS
    val queueUpdate = persistMealGroup(
      group.submissions,
      pendingSubmissions,
      firstPhoto,
      ImageMealSubmissionPayload(groupedPhotos).encode(),
      photo.occurredAt,
      scheduledAt,
    )
    database.photoProcessingDao().insertResult(
      PhotoResultEntity(photo.version, photo.imageUri, photo.capturedAt, true),
    )
    return queueUpdate
  }

  private fun persistMealGroup(
    submissions: List<MealSubmissionEntity>,
    pendingSubmissions: List<MealSubmissionEntity>,
    firstPhoto: ImageMealSubmissionPayload.Photo,
    imagePayload: String,
    occurredAtForNewGroup: String,
    scheduledAt: Long,
  ): FoodPhotoQueueUpdate {
    val firstPhotoSubmission = submissions.firstOrNull { submission ->
      ImageMealSubmissionPayload.decode(checkNotNull(submission.imagePayload)).photos.contains(firstPhoto)
    }
    val canonicalSubmission = firstPhotoSubmission ?: submissions.minByOrNull { it.createdAt }
    val occurredAt = firstPhotoSubmission?.occurredAt ?: occurredAtForNewGroup
    if (canonicalSubmission == null) {
      val latestCreatedAt = pendingSubmissions.maxOfOrNull { it.createdAt } ?: 0L
      val mealId = UUID.randomUUID().toString()
      repository.insert(MealSubmissionEntity(
        mealId = mealId,
        type = MealSubmissionEntity.TYPE_IMAGE,
        imagePayload = imagePayload,
        text = null,
        occurredAt = occurredAt,
        nextAutomaticAttemptAt = scheduledAt,
        createdAt = maxOf(System.currentTimeMillis(), latestCreatedAt + 1),
      ))
      return FoodPhotoQueueUpdate(mealId)
    }

    check(repository.updatePendingImagePayload(
      canonicalSubmission.mealId,
      imagePayload,
      occurredAt,
      scheduledAt,
    ))
    val removedMealIds = submissions
      .filter { it.mealId != canonicalSubmission.mealId }
      .map { it.mealId }
    removedMealIds.forEach(repository::delete)
    return FoodPhotoQueueUpdate(canonicalSubmission.mealId, removedMealIds)
  }

  companion object {
    const val FOOD_MEAL_WINDOW_MILLIS = 15 * 60 * 1000L
  }
}

private data class FoodPhotoQueueChanges(
  val mealIdsToSchedule: MutableSet<String> = linkedSetOf(),
  val mealIdsToCancel: MutableSet<String> = linkedSetOf(),
)

private data class FoodPhotoQueueUpdate(
  val mealId: String,
  val removedMealIds: List<String> = emptyList(),
)

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
