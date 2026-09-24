package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import net.ambitious.android.mealrelay.setup.InitialSetupActions
import net.ambitious.android.mealrelay.setup.MealRelaySetupCoordinator
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsViewModel
import net.ambitious.android.mealrelay.ui.main.MealRelayMainContent
import net.ambitious.android.mealrelay.ui.main.MealRelayMainViewModel
import net.ambitious.android.mealrelay.ui.settings.MealRelaySettingsViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MealRelayMainActivity : ComponentActivity() {
  @Inject lateinit var setupCoordinator: MealRelaySetupCoordinator
  private val failedMealSubmissionsViewModel by viewModels<FailedMealSubmissionsViewModel>()
  private val mainViewModel by viewModels<MealRelayMainViewModel>()
  private val settingsViewModel by viewModels<MealRelaySettingsViewModel>()
  private val requestInitialNotificationPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { setupCoordinator.onInitialNotificationPermissionRequestCompleted(initialSetupActions) }
  private val requestInitialPhotoPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { setupCoordinator.onInitialPhotoPermissionRequestCompleted(initialSetupActions) }
  private val requestSettingsNotificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { settingsViewModel.refresh() }
  private val requestSettingsPhotoPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { setupCoordinator.onSettingsPhotoAccessChanged(settingsViewModel::refresh) }
  private val openUnusedAppRestrictionsSettingsLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { settingsViewModel.refresh() }
  private val openPhotoApplicationSettingsLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { setupCoordinator.onSettingsPhotoAccessChanged(settingsViewModel::refresh) }
  private val openNotificationApplicationSettingsLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { settingsViewModel.refresh() }
  private val initialSetupActions: InitialSetupActions by lazy {
    InitialSetupActions(
      requestNotificationPermission = { requestInitialNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
      requestPhotoPermission = {
        requestInitialPhotoPermissionsLauncher.launch(
          arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        )
      },
      requestAuthorization = mainViewModel::requestAuthorization,
      completePermissionChecks = settingsViewModel::completeInitialSetupPermissionChecks,
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
        onOpenPhotoApplicationSettings = ::openPhotoApplicationSettings,
        onOpenNotificationApplicationSettings = ::openNotificationApplicationSettings,
        onOpenUnusedAppRestrictionsSettings = ::openUnusedAppRestrictionsSettings,
        onDismissUnusedAppRestrictionsGuide = settingsViewModel::dismissUnusedAppRestrictionsGuide,
        onStartAuthorization = ::startAuthorization,
      )
    }
    mainViewModel.recoverPendingSubmissions()
    setupCoordinator.startInitialSetup(initialSetupActions)
  }

  override fun onResume() {
    super.onResume()
    failedMealSubmissionsViewModel.load()
    settingsViewModel.refresh()
  }

  private fun requestSettingsPhotoPermission() {
    setupCoordinator.requestSettingsPhotoPermission(
      requestPhotoPermission = {
        requestSettingsPhotoPermissionsLauncher.launch(
          arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        )
      },
      refreshSettings = settingsViewModel::refresh,
    )
  }

  private fun requestSettingsNotificationPermission() {
    setupCoordinator.requestSettingsNotificationPermission(
      requestNotificationPermission = {
        requestSettingsNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      },
      refreshSettings = settingsViewModel::refresh,
    )
  }

  private fun openPhotoApplicationSettings() {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      android.net.Uri.fromParts("package", packageName, null),
    )
    openPhotoApplicationSettingsLauncher.launch(intent)
  }

  private fun openNotificationApplicationSettings() {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      android.net.Uri.fromParts("package", packageName, null),
    )
    openNotificationApplicationSettingsLauncher.launch(intent)
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
