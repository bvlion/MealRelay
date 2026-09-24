package net.ambitious.android.mealrelay.submission.network

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionImage
import org.json.JSONArray
import javax.inject.Inject

class ImageMealSubmissionImageSource @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun read(imageUri: String): ImageMealSubmissionImage {
    val uri = Uri.parse(imageUri)
    val mediaType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) {
      "Image URI cannot be opened"
    }.use { it.readBytes() }
    return ImageMealSubmissionImage(bytes, mediaType)
  }

  fun readAll(imageUris: String): List<ImageMealSubmissionImage> {
    val uris = if (imageUris.startsWith("[")) {
      val images = JSONArray(imageUris)
      (0 until images.length()).map { images.getJSONObject(it).getString("uri") }
    } else {
      listOf(imageUris)
    }
    return uris.map(::read)
  }
}
