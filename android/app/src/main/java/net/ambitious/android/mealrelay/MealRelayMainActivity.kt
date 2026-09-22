package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsScreen
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsViewModel

import javax.inject.Inject

@AndroidEntryPoint
class MealRelayMainActivity : ComponentActivity() {
  @Inject lateinit var tokenStore: MealRelayTokenStore
  private val viewModel by viewModels<FailedMealSubmissionsViewModel>()
  private val requestNotificationPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { requestPhotoPermission() }
  private val requestPhotoPermissions = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
    }
    requestAuthorization()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LaunchedEffect(Unit) { viewModel.load() }
      FailedMealSubmissionsScreen(viewModel.state, viewModel::retry)
    }
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
      requestPhotoPermission()
    } else {
      requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.load()
  }

  private fun requestPhotoPermission() {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
      requestAuthorization()
    } else {
      requestPhotoPermissions.launch(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
      )
    }
  }

  private fun requestAuthorization() {
    lifecycleScope.launch {
      val hasToken = withContext(Dispatchers.IO) { tokenStore.read() != null }
      if (!hasToken) startActivity(Intent(this@MealRelayMainActivity, MealRelayAuthorizationActivity::class.java))
    }
  }
}
