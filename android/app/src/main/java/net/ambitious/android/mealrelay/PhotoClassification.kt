package net.ambitious.android.mealrelay

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri

class PhotoClassification(context: Context) : AutoCloseable {
  private val contentResolver = context.contentResolver
  private val applicationContext = context.applicationContext
  private var classifier: FoodClassifier? = null

  fun prepareClassifier(): (Uri) -> Boolean {
    if (classifier == null) classifier = FoodClassifier(applicationContext)
    return this::isFood
  }

  fun isFood(uri: Uri): Boolean {
    val classifier = checkNotNull(classifier)
    val source = ImageDecoder.createSource(contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
      decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
    }
    return try {
      classifier.isFood(bitmap)
    } finally {
      bitmap.recycle()
    }
  }

  override fun close() {
    classifier?.close()
    classifier = null
  }
}
