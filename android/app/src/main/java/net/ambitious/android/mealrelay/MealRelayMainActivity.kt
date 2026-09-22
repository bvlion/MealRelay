package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import dagger.hilt.android.AndroidEntryPoint
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsScreen
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsViewModel
import net.ambitious.android.mealrelay.ui.main.MainAuthorizationState
import net.ambitious.android.mealrelay.ui.main.MealRelayMainViewModel

@AndroidEntryPoint
class MealRelayMainActivity : ComponentActivity() {
  private val failedMealSubmissionsViewModel by viewModels<FailedMealSubmissionsViewModel>()
  private val mainViewModel by viewModels<MealRelayMainViewModel>()
  private val requestNotificationPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { requestPhotoPermission() }
  private val requestPhotoPermissions = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
    }
    mainViewModel.requestAuthorization()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val authorizationState by mainViewModel.authorizationState.collectAsState()
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
      FailedMealSubmissionsScreen(
        failedMealSubmissionsViewModel.state,
        failedMealSubmissionsViewModel::retry,
      )
    }
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
      requestPhotoPermission()
    } else {
      requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  override fun onResume() {
    super.onResume()
    failedMealSubmissionsViewModel.load()
  }

  private fun requestPhotoPermission() {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
      mainViewModel.requestAuthorization()
    } else {
      requestPhotoPermissions.launch(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
      )
    }
  }

}
