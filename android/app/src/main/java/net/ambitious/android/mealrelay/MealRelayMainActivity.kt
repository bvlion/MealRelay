package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsViewModel
import net.ambitious.android.mealrelay.ui.main.MealRelayMainContent
import net.ambitious.android.mealrelay.ui.main.MealRelayMainViewModel
import net.ambitious.android.mealrelay.ui.settings.InitialSetupPermissionFlow
import net.ambitious.android.mealrelay.ui.settings.MealRelaySettingsViewModel

@AndroidEntryPoint
class MealRelayMainActivity : ComponentActivity() {
  private val failedMealSubmissionsViewModel by viewModels<FailedMealSubmissionsViewModel>()
  private val mainViewModel by viewModels<MealRelayMainViewModel>()
  private val settingsViewModel by viewModels<MealRelaySettingsViewModel>()
  private val requestInitialNotificationPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { initialSetupPermissionFlow.onNotificationPermissionRequestCompleted() }
  private val requestInitialPhotoPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    onPhotoPermissionCheckCompleted()
    settingsViewModel.completeInitialSetupPermissionChecks()
  }
  private val requestSettingsNotificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { settingsViewModel.refresh() }
  private val requestSettingsPhotoPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    onPhotoPermissionCheckCompleted()
    settingsViewModel.refresh()
  }
  private val openUnusedAppRestrictionsSettingsLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { settingsViewModel.refresh() }
  private val openApplicationSettingsLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { settingsViewModel.refresh() }
  private val initialSetupPermissionFlow: InitialSetupPermissionFlow by lazy {
    InitialSetupPermissionFlow(
      requestNotificationPermission = {
        requestInitialNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      },
      requestPhotoAccess = ::requestInitialPhotoPermission,
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MealRelayMainContent(
        failedMealSubmissionsState = failedMealSubmissionsViewModel.state,
        authorizationState = mainViewModel.authorizationState,
        settingsState = settingsViewModel.state,
        onRetryFailedMeal = failedMealSubmissionsViewModel::retry,
        onSubmitManualMeal = { text -> mainViewModel.submitManualMeal(text) },
        onRequestPhotoPermission = ::requestSettingsPhotoPermission,
        onRequestNotificationPermission = ::requestSettingsNotificationPermission,
        onOpenApplicationSettings = ::openApplicationSettings,
        onOpenUnusedAppRestrictionsSettings = ::openUnusedAppRestrictionsSettings,
        onDismissUnusedAppRestrictionsGuide = settingsViewModel::dismissUnusedAppRestrictionsGuide,
        onStartAuthorization = ::startAuthorization,
      )
    }
    mainViewModel.recoverPendingSubmissions()
    initialSetupPermissionFlow.start(
      hasNotificationPermission =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    )
  }

  override fun onResume() {
    super.onResume()
    failedMealSubmissionsViewModel.load()
    settingsViewModel.refresh()
  }

  private fun requestInitialPhotoPermission() {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      onPhotoPermissionCheckCompleted()
      settingsViewModel.completeInitialSetupPermissionChecks()
    } else {
      requestInitialPhotoPermissionsLauncher.launch(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
      )
    }
  }

  private fun requestSettingsPhotoPermission() {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      onPhotoPermissionCheckCompleted()
      settingsViewModel.refresh()
    } else {
      requestSettingsPhotoPermissionsLauncher.launch(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
      )
    }
  }

  private fun onPhotoPermissionCheckCompleted() {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
    }
    mainViewModel.requestAuthorization()
    settingsViewModel.refresh()
  }

  private fun requestSettingsNotificationPermission() {
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
      settingsViewModel.refresh()
    } else {
      requestSettingsNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private fun openApplicationSettings() {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      android.net.Uri.fromParts("package", packageName, null),
    )
    openApplicationSettingsLauncher.launch(intent)
  }

  private fun openUnusedAppRestrictionsSettings() {
    val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(this, packageName)
    openUnusedAppRestrictionsSettingsLauncher.launch(intent)
  }

  private fun startAuthorization() {
    mainViewModel.consumeAuthorizationRequest()
    startActivity(Intent(this, MealRelayAuthorizationActivity::class.java))
  }

}
