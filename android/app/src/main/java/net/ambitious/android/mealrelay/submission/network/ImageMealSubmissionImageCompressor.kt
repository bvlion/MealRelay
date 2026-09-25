package net.ambitious.android.mealrelay.submission.network

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionImage
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ImageMealSubmissionImageCompressor @Inject constructor() {
  fun compress(image: ImageMealSubmissionImage): ImageMealSubmissionImage {
    if (image.bytes.size <= MAX_IMAGE_BYTES) return image

    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(image.bytes))) { decoder, info, _ ->
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      val scale = minOf(1.0, MAX_DECODED_DIMENSION.toDouble() / maxOf(info.size.width, info.size.height))
      if (scale < 1.0) {
        decoder.setTargetSize(
          (info.size.width * scale).toInt().coerceAtLeast(1),
          (info.size.height * scale).toInt().coerceAtLeast(1),
        )
      }
    }
    var scaledBitmap = bitmap
    try {
      while (true) {
        for (quality in JPEG_QUALITIES) {
          val output = ByteArrayOutputStream()
          scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
          val bytes = output.toByteArray()
          if (bytes.size <= MAX_IMAGE_BYTES) {
            return ImageMealSubmissionImage(bytes, JPEG_MEDIA_TYPE)
          }
        }
        val nextBitmap = Bitmap.createScaledBitmap(
          scaledBitmap,
          (scaledBitmap.width * SCALE_FACTOR).toInt().coerceAtLeast(1),
          (scaledBitmap.height * SCALE_FACTOR).toInt().coerceAtLeast(1),
          true,
        )
        if (nextBitmap === scaledBitmap) error("Image cannot be reduced below the upload size limit")
        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        scaledBitmap = nextBitmap
      }
    } finally {
      if (scaledBitmap !== bitmap) scaledBitmap.recycle()
      bitmap.recycle()
    }
  }

  companion object {
    const val MAX_IMAGE_BYTES = 1024 * 1024
    private const val MAX_DECODED_DIMENSION = 2048
    private const val SCALE_FACTOR = 0.8
    private const val JPEG_MEDIA_TYPE = "image/jpeg"
    private val JPEG_QUALITIES = intArrayOf(90, 80, 70, 60, 50)
  }
}
