package net.ambitious.android.mealrelay.submission.network.model

data class ImageMealSubmissionRequest(
  val image: ImageMealSubmissionImage,
  val capturedAt: String,
  val mealId: String,
)

data class ImageMealSubmissionImage(val bytes: ByteArray, val mediaType: String)
