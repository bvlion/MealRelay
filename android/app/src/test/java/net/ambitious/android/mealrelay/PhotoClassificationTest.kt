package net.ambitious.android.mealrelay

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class PhotoClassificationTest {
  @Test
  fun emptyResourceScopeDoesNotOpenModel() {
    val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
      override fun getApplicationContext(): Context = this
      override fun getAssets(): AssetManager = error("model asset was requested")
    }
    PhotoClassification(context).use { }
    PhotoClassification(context).use { classification ->
      assertThrows(IllegalStateException::class.java) { classification.prepareClassifier() }
    }
  }
}
