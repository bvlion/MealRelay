package net.ambitious.android.mealrelay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodClassifierTest {
  @Test
  fun foodFixtureIsAccepted() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.context.assets.open("food.jpg").use { inputStream ->
      FoodClassifier(instrumentation.targetContext).use { classifier ->
        val bitmap = requireNotNull(BitmapFactory.decodeStream(inputStream))
        assertTrue(classifier.isFood(bitmap))
        bitmap.recycle()
      }
    }
  }

  @Test
  fun nonFoodFixtureIsRejected() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.context.assets.open("non_food.jpg").use { inputStream ->
      FoodClassifier(instrumentation.targetContext).use { classifier ->
        val bitmap = requireNotNull(BitmapFactory.decodeStream(inputStream))
        assertFalse(classifier.isFood(bitmap))
        bitmap.recycle()
      }
    }
  }

  @Test
  fun hardwareBitmapIsAccepted() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.context.assets.open("food.jpg").use { inputStream ->
      FoodClassifier(instrumentation.targetContext).use { classifier ->
        val source = ImageDecoder.createSource(ByteBuffer.wrap(inputStream.readAllBytes()))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
          decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
          decoder.setTargetSize(224, 224)
        }
        assertEquals(Bitmap.Config.HARDWARE, bitmap.config)
        assertEquals(224, bitmap.width)
        assertEquals(224, bitmap.height)
        assertTrue(classifier.isFood(bitmap))
        bitmap.recycle()
      }
    }
  }
}
