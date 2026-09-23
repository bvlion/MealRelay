package net.ambitious.android.mealrelay.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialSetupPermissionFlowTest {
  @Test
  fun notificationCheckContinuesToPhotoAccessAfterPermissionRequestCompletes() {
    var notificationRequests = 0
    var photoAccessRequests = 0
    val flow = InitialSetupPermissionFlow(
      requestNotificationPermission = { notificationRequests++ },
      requestPhotoAccess = { photoAccessRequests++ },
    )

    flow.start(hasNotificationPermission = false)
    assertEquals(1, notificationRequests)
    assertEquals(0, photoAccessRequests)

    flow.onNotificationPermissionRequestCompleted()
    assertEquals(1, photoAccessRequests)
  }

  @Test
  fun existingNotificationPermissionSkipsItsPromptAndChecksPhotoAccess() {
    var notificationRequests = 0
    var photoAccessRequests = 0
    val flow = InitialSetupPermissionFlow(
      requestNotificationPermission = { notificationRequests++ },
      requestPhotoAccess = { photoAccessRequests++ },
    )

    flow.start(hasNotificationPermission = true)

    assertEquals(0, notificationRequests)
    assertEquals(1, photoAccessRequests)
  }
}
