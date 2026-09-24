package net.ambitious.android.mealrelay.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import net.ambitious.android.mealrelay.data.settings.MealRelaySetupPreferences
import net.ambitious.android.mealrelay.photo.PhotoEnrollmentScheduler
import javax.inject.Inject

class InitialSetupActions(
  val requestNotificationPermission: () -> Unit,
  val requestPhotoPermission: () -> Unit,
  val requestAuthorization: () -> Unit,
  val completePermissionChecks: () -> Unit,
)

class MealRelaySetupCoordinator @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val setupPreferences: MealRelaySetupPreferences,
  private val photoEnrollmentScheduler: PhotoEnrollmentScheduler,
) {
  fun startInitialSetup(actions: InitialSetupActions) {
    if (shouldRequestInitialNotificationPermission()) {
      actions.requestNotificationPermission()
    } else {
      requestInitialPhotoAccess(actions)
    }
  }

  fun onInitialNotificationPermissionRequestCompleted(actions: InitialSetupActions) {
    requestInitialPhotoAccess(actions)
  }

  fun onInitialPhotoPermissionRequestCompleted(actions: InitialSetupActions) {
    completeInitialPhotoSetup(actions)
  }

  fun requestSettingsPhotoPermission(
    requestPhotoPermission: () -> Unit,
    refreshSettings: () -> Unit,
  ) {
    if (hasFullPhotoAccess()) {
      photoEnrollmentScheduler.enqueue()
      refreshSettings()
    } else {
      requestPhotoPermission()
    }
  }

  fun onSettingsPhotoAccessChanged(refreshSettings: () -> Unit) {
    refreshSettingsPhotoAccess(refreshSettings)
  }

  fun requestSettingsNotificationPermission(
    requestNotificationPermission: () -> Unit,
    refreshSettings: () -> Unit,
  ) {
    if (hasNotificationPermission()) {
      refreshSettings()
    } else {
      requestNotificationPermission()
    }
  }

  private fun shouldRequestInitialNotificationPermission(): Boolean {
    if (setupPreferences.hasRequestedInitialNotificationPermission()) return false
    setupPreferences.markInitialNotificationPermissionRequested()
    return !hasNotificationPermission()
  }

  private fun requestInitialPhotoAccess(actions: InitialSetupActions) {
    if (hasFullPhotoAccess()) {
      completeInitialPhotoSetup(actions)
    } else {
      actions.requestPhotoPermission()
    }
  }

  private fun completeInitialPhotoSetup(actions: InitialSetupActions) {
    if (hasFullPhotoAccess()) photoEnrollmentScheduler.enqueue()
    actions.requestAuthorization()
    actions.completePermissionChecks()
  }

  private fun refreshSettingsPhotoAccess(refreshSettings: () -> Unit) {
    if (hasFullPhotoAccess()) photoEnrollmentScheduler.enqueue()
    refreshSettings()
  }

  private fun hasFullPhotoAccess(): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

  private fun hasNotificationPermission(): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
