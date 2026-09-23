package net.ambitious.android.mealrelay.ui.settings

class SettingsPhotoAccessFlow(
  private val enqueuePhotoEnrollment: () -> Unit,
  private val refreshSettings: () -> Unit,
) {
  fun onSettingsReturned(hasFullPhotoAccess: Boolean) {
    if (hasFullPhotoAccess) enqueuePhotoEnrollment()
    refreshSettings()
  }
}
