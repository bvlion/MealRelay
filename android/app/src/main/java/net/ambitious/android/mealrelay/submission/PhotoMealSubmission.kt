package net.ambitious.android.mealrelay.submission

import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class PhotoMealSubmission @Inject constructor(private val queue: MealSubmissionQueue) {
  fun enqueue(uri: Uri, capturedAt: Long, version: String) {
    val occurredAt = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)
      .format(Instant.ofEpochMilli(capturedAt).atZone(ZoneId.systemDefault()))
    queue.enqueueFoodPhoto(
      uri,
      capturedAt,
      version,
      occurredAt,
    )
  }
}
