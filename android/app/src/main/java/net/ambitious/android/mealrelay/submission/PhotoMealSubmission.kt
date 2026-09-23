package net.ambitious.android.mealrelay.submission

import android.net.Uri
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import java.time.Instant
import javax.inject.Inject

class PhotoMealSubmission @Inject constructor(private val queue: MealSubmissionQueue) {
  fun enqueue(uri: Uri, capturedAt: Long) {
    queue.enqueue(
      MealSubmissionDraft(
        type = MealSubmissionEntity.TYPE_IMAGE,
        imageUri = uri.toString(),
        text = null,
        occurredAt = Instant.ofEpochMilli(capturedAt).toString(),
      ),
    )
  }
}
