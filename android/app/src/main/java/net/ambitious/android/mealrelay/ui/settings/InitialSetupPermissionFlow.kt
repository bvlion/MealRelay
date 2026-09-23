package net.ambitious.android.mealrelay.ui.settings

class InitialSetupPermissionFlow(
  private val requestNotificationPermission: () -> Unit,
  private val requestPhotoAccess: () -> Unit,
) {
  fun start(hasNotificationPermission: Boolean) {
    if (hasNotificationPermission) {
      requestPhotoAccess()
    } else {
      requestNotificationPermission()
    }
  }

  fun onNotificationPermissionRequestCompleted() {
    requestPhotoAccess()
  }
}
