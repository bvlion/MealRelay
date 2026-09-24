package net.ambitious.android.mealrelay.submission

import android.net.Uri
import net.ambitious.android.mealrelay.data.photo.PhotoResultEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class PhotoMealSubmission @Inject constructor(private val queue: MealSubmissionQueue) {
  fun enqueue(uri: Uri, capturedAt: Long, version: String) = enqueueAll(listOf(
    PhotoResultEntity(version, uri.toString(), capturedAt, true),
  ))

  fun enqueueAll(photos: List<PhotoResultEntity>) {
    val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)
    queue.enqueueFoodPhotos(photos.map { photo ->
      FoodPhotoSubmissionDraft(
        imageUri = photo.uri,
        capturedAt = photo.capturedAt,
        version = photo.version,
        occurredAt = formatter.format(
          Instant.ofEpochMilli(photo.capturedAt).atZone(ZoneId.systemDefault()),
        ),
      )
    })
  }
}
