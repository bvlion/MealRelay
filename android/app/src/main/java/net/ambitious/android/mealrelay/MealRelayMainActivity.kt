package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsViewModel
import net.ambitious.android.mealrelay.ui.main.MainAuthorizationState
import net.ambitious.android.mealrelay.ui.main.MealRelayMainScreen
import net.ambitious.android.mealrelay.ui.main.MealRelayMainViewModel
import net.ambitious.android.mealrelay.ui.settings.MealRelaySettingsScreen
import net.ambitious.android.mealrelay.ui.settings.MealRelaySettingsViewModel

@AndroidEntryPoint
class MealRelayMainActivity : ComponentActivity() {
  private val failedMealSubmissionsViewModel by viewModels<FailedMealSubmissionsViewModel>()
  private val mainViewModel by viewModels<MealRelayMainViewModel>()
  private val settingsViewModel by viewModels<MealRelaySettingsViewModel>()
  private val requestNotificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) {
    requestPhotoPermission()
    settingsViewModel.refresh()
  }
  private val requestPhotoPermissions = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
    }
    mainViewModel.requestAuthorization()
    settingsViewModel.refresh()
  }
  private val openUnusedAppRestrictionsSettings = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { settingsViewModel.refresh() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val authorizationState by mainViewModel.authorizationState.collectAsState()
      val settingsState by settingsViewModel.state.collectAsState()
      var isSettingsVisible by rememberSaveable { mutableStateOf(false) }
      LaunchedEffect(Unit) {
        failedMealSubmissionsViewModel.load()
        mainViewModel.recoverPendingSubmissions()
      }
      LaunchedEffect(authorizationState) {
        if (authorizationState == MainAuthorizationState.Required) {
          mainViewModel.consumeAuthorizationRequest()
          startActivity(Intent(this@MealRelayMainActivity, MealRelayAuthorizationActivity::class.java))
        }
      }
      if (isSettingsVisible) {
        MealRelaySettingsScreen(
          state = settingsState,
          onBack = { isSettingsVisible = false },
          onRequestPhotoPermission = ::requestPhotoPermission,
          onRequestNotificationPermission = ::requestNotificationPermission,
          onOpenUnusedAppRestrictions = ::openUnusedAppRestrictionsSettings,
        )
      } else {
        MealRelayMainScreen(
          failedMealSubmissionsViewModel.state,
          failedMealSubmissionsViewModel::retry,
          { text -> mainViewModel.submitManualMeal(text) },
          onOpenSettings = { isSettingsVisible = true },
        )
      }
      if (settingsState.shouldShowUnusedAppRestrictionsGuide) {
        AlertDialog(
          onDismissRequest = settingsViewModel::dismissUnusedAppRestrictionsGuide,
          title = { Text(stringResource(R.string.unused_app_restrictions_title)) },
          text = { Text(stringResource(R.string.unused_app_restrictions_explanation)) },
          confirmButton = {
            TextButton(onClick = {
              settingsViewModel.dismissUnusedAppRestrictionsGuide()
              openUnusedAppRestrictionsSettings()
            }) { Text(stringResource(R.string.unused_app_restrictions_open_settings)) }
          },
          dismissButton = {
            TextButton(onClick = settingsViewModel::dismissUnusedAppRestrictionsGuide) {
              Text(stringResource(R.string.unused_app_restrictions_later))
            }
          },
        )
      }
    }
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
      requestPhotoPermission()
    } else {
      requestNotificationPermission()
    }
  }

  override fun onResume() {
    super.onResume()
    failedMealSubmissionsViewModel.load()
    settingsViewModel.refresh()
  }

  private fun requestPhotoPermission() {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
      mainViewModel.requestAuthorization()
      settingsViewModel.refresh()
    } else {
      requestPhotoPermissions.launch(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
      )
    }
  }

  private fun requestNotificationPermission() {
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
      settingsViewModel.refresh()
      requestPhotoPermission()
    } else {
      requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private fun openUnusedAppRestrictionsSettings() {
    val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(this, packageName)
    openUnusedAppRestrictionsSettings.launch(intent)
  }

}
