package net.ambitious.android.mealrelay

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class PhotoPermissionActivity : ComponentActivity() {
  private val requestPhotoPermissions = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
    }
    startAuthorization()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
      startAuthorization()
    } else if (savedInstanceState == null) {
      requestPhotoPermissions.launch(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
      )
    }
  }

  private fun startAuthorization() {
    startActivity(Intent(this, MealRelayAuthorizationActivity::class.java))
    finish()
  }
}
