package net.ambitious.android.mealrelay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import org.tensorflow.lite.Interpreter

class FoodClassifier(context: Context) : AutoCloseable {
  private val interpreter: Interpreter
  private val inputBuffer = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3)
    .order(ByteOrder.nativeOrder())
  private val output = Array(1) { ByteArray(1) }

  init {
    context.assets.open(MODEL_ASSET_NAME).use { inputStream ->
      val modelBytes = inputStream.readAllBytes()
      val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
        .order(ByteOrder.nativeOrder())
      modelBuffer.put(modelBytes)
      modelBuffer.rewind()
      interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2))
    }
  }

  @Synchronized
  fun isFood(bitmap: Bitmap): Boolean {
    require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
      "Bitmap must contain a readable image"
    }

    val readableBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
      requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) {
        "Hardware Bitmap could not be copied"
      }
    } else {
      bitmap
    }
    val scale = minOf(
      IMAGE_SIZE.toFloat() / readableBitmap.width,
      IMAGE_SIZE.toFloat() / readableBitmap.height,
    )
    val scaledWidth = maxOf(1, (readableBitmap.width * scale).roundToInt())
    val scaledHeight = maxOf(1, (readableBitmap.height * scale).roundToInt())
    val scaledBitmap = Bitmap.createScaledBitmap(
      readableBitmap,
      scaledWidth,
      scaledHeight,
      true,
    )
    val modelBitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888)
    modelBitmap.eraseColor(Color.BLACK)
    Canvas(modelBitmap).drawBitmap(
      scaledBitmap,
      (IMAGE_SIZE - scaledWidth) / 2.0f,
      (IMAGE_SIZE - scaledHeight) / 2.0f,
      null,
    )

    val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
    modelBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
    inputBuffer.rewind()
    for (pixel in pixels) {
      inputBuffer.put(Color.red(pixel).toByte())
      inputBuffer.put(Color.green(pixel).toByte())
      inputBuffer.put(Color.blue(pixel).toByte())
    }
    inputBuffer.rewind()
    interpreter.run(inputBuffer, output)

    if (scaledBitmap !== readableBitmap) {
      scaledBitmap.recycle()
    }
    if (readableBitmap !== bitmap) {
      readableBitmap.recycle()
    }
    modelBitmap.recycle()
    return (output[0][0].toInt() and 0xff) >= FOOD_PROBABILITY_THRESHOLD_QUANTIZED
  }

  override fun close() {
    interpreter.close()
  }

  private companion object {
    const val MODEL_ASSET_NAME = "food_classifier_int8.tflite"
    const val IMAGE_SIZE = 224
    const val FOOD_PROBABILITY_THRESHOLD_QUANTIZED = 153
  }
}
