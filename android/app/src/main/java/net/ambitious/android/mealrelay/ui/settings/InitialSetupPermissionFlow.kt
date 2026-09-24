package net.ambitious.android.mealrelay.ui.settings

class InitialSetupPermissionFlow(
  private val requestNotificationPermission: () -> Unit,
  private val requestPhotoAccess: () -> Unit,
  private val onPhotoAccessCheckCompleted: () -> Unit,
) {
  fun start(shouldRequestNotificationPermission: Boolean) {
    if (shouldRequestNotificationPermission) {
      requestNotificationPermission()
    } else {
      requestPhotoAccess()
    }
  }

  fun onNotificationPermissionRequestCompleted() {
    requestPhotoAccess()
  }

  fun onPhotoAccessRequestCompleted() {
    onPhotoAccessCheckCompleted()
  }
}
