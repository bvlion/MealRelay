package net.ambitious.android.mealrelay.submission.network

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionImage
import java.io.ByteArrayOutputStream
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class ImageMealSubmissionImageCompressorTest {
  private val compressor = ImageMealSubmissionImageCompressor()

  @Test
  fun oversizedImageIsResizedAndEncodedBelowOneMebibyte() {
    val bitmap = Bitmap.createBitmap(1800, 1800, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(1800 * 1800)
    val random = Random(30)
    pixels.indices.forEach { index -> pixels[index] = 0xff000000.toInt() or random.nextInt(0x1000000) }
    bitmap.setPixels(pixels, 0, 1800, 0, 0, 1800, 1800)
    val original = ByteArrayOutputStream().also {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
      bitmap.recycle()
    }.toByteArray()
    assertTrue(original.size > ImageMealSubmissionImageCompressor.MAX_IMAGE_BYTES)

    val compressed = compressor.compress(ImageMealSubmissionImage(original, "image/jpeg"))

    assertTrue(compressed.bytes.size <= ImageMealSubmissionImageCompressor.MAX_IMAGE_BYTES)
    assertEquals("image/jpeg", compressed.mediaType)
    assertNotSame(original, compressed.bytes)
  }

  @Test
  fun imageAlreadyWithinLimitIsPreserved() {
    val image = ImageMealSubmissionImage(byteArrayOf(1, 2, 3), "image/webp")

    assertTrue(compressor.compress(image) === image)
  }
}
