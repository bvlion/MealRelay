package net.ambitious.android.mealrelay.submission

import org.json.JSONArray
import org.json.JSONObject

data class ImageMealSubmissionPayload(val photos: List<Photo>) {
  data class Photo(val uri: String, val capturedAt: Long?)

  fun encode(): String = JSONArray().apply {
    photos.forEach { photo ->
      put(JSONObject().put("uri", photo.uri).apply {
        photo.capturedAt?.let { put("capturedAt", it) }
      })
    }
  }.toString()

  companion object {
    fun decode(value: String): ImageMealSubmissionPayload {
      if (!value.startsWith("[")) return ImageMealSubmissionPayload(listOf(Photo(value, null)))
      val images = JSONArray(value)
      return ImageMealSubmissionPayload((0 until images.length()).map { index ->
        val image = images.getJSONObject(index)
        Photo(
          uri = image.getString("uri"),
          capturedAt = if (image.has("capturedAt")) image.getLong("capturedAt") else null,
        )
      })
    }
  }
}
