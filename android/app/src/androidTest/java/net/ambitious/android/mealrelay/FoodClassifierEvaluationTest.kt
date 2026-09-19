package net.ambitious.android.mealrelay

import android.graphics.ImageDecoder
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter

@RunWith(AndroidJUnit4::class)
class FoodClassifierEvaluationTest {
  @Test
  fun evaluateExternalImagesWithAndroidPreprocessing() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val evaluationRoot = InstrumentationRegistry.getArguments().getString("evaluationRoot")
    if (evaluationRoot == null) {
      return
    }

    val manifestDescriptor = instrumentation.uiAutomation.executeShellCommand(
      "cat $evaluationRoot/manifest.tsv",
    )
    val manifest = ParcelFileDescriptor.AutoCloseInputStream(manifestDescriptor)
      .bufferedReader()
      .use { it.readLines() }
    val results = StringBuilder()

    FoodClassifier(instrumentation.targetContext).use { classifier ->
      val interpreterField = FoodClassifier::class.java.getDeclaredField("interpreter")
      interpreterField.isAccessible = true
      val evaluationModel = InstrumentationRegistry.getArguments().getString("evaluationModel")
      if (evaluationModel != null) {
        val modelDescriptor = instrumentation.uiAutomation.executeShellCommand(
          "cat $evaluationModel",
        )
        val modelBytes = ParcelFileDescriptor.AutoCloseInputStream(modelDescriptor).use {
          it.readAllBytes()
        }
        (interpreterField.get(classifier) as Interpreter).close()
        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
        modelBuffer.put(modelBytes)
        modelBuffer.rewind()
        interpreterField.set(
          classifier,
          Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2)),
        )
      }

      val outputField = FoodClassifier::class.java.getDeclaredField("output")
      outputField.isAccessible = true
      for (line in manifest) {
        val (label, group, file) = line.split('\t')
        val imageDescriptor = instrumentation.uiAutomation.executeShellCommand(
          "cat $evaluationRoot/images/$file",
        )
        val imageBytes = ParcelFileDescriptor.AutoCloseInputStream(imageDescriptor).use {
          it.readAllBytes()
        }
        val source = ImageDecoder.createSource(ByteBuffer.wrap(imageBytes))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
          decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
        }
        val isFood = classifier.isFood(bitmap)
        @Suppress("UNCHECKED_CAST")
        val output = outputField.get(classifier) as Array<ByteArray>
        val probabilityQuantized = output[0][0].toInt() and 0xff
        assertEquals(probabilityQuantized >= 103, isFood)
        results.append(label)
          .append('\t')
          .append(group)
          .append('\t')
          .append(file)
          .append('\t')
          .append(probabilityQuantized)
          .append('\n')
        bitmap.recycle()
      }
    }

    File(
      instrumentation.targetContext.filesDir,
      "food_classifier_evaluation.tsv",
    ).writeText(results.toString())
  }
}
