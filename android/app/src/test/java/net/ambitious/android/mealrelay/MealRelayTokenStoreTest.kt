package net.ambitious.android.mealrelay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MealRelayTokenStoreTest {
  @Test
  fun tokenIsEncryptedAtRestAndCanBeReadAgain() {
    val context = RuntimeEnvironment.getApplication()
    val store = MealRelayTokenStore(context)
    store.write("test-device-token")

    val stored = context.getSharedPreferences("mealrelay_auth", Context.MODE_PRIVATE)
      .getString("token", null)
    assertNotEquals("test-device-token", stored)
    assertEquals("test-device-token", MealRelayTokenStore(context).read())
  }
}
