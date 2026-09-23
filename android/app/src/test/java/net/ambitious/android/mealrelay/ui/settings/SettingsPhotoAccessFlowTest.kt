package net.ambitious.android.mealrelay.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPhotoAccessFlowTest {
  @Test
  fun returningWithFullPhotoAccessEnqueuesPhotoEnrollmentAndRefreshesSettings() {
    val actions = mutableListOf<String>()
    val flow = SettingsPhotoAccessFlow(
      enqueuePhotoEnrollment = { actions.add("photo enrollment") },
      refreshSettings = { actions.add("settings refresh") },
    )

    flow.onSettingsReturned(hasFullPhotoAccess = true)

    assertEquals(listOf("photo enrollment", "settings refresh"), actions)
  }

  @Test
  fun returningWithoutFullPhotoAccessOnlyRefreshesSettings() {
    val actions = mutableListOf<String>()
    val flow = SettingsPhotoAccessFlow(
      enqueuePhotoEnrollment = { actions.add("photo enrollment") },
      refreshSettings = { actions.add("settings refresh") },
    )

    flow.onSettingsReturned(hasFullPhotoAccess = false)

    assertEquals(listOf("settings refresh"), actions)
  }
}
