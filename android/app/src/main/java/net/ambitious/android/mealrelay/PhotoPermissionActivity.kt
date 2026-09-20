package net.ambitious.android.mealrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle

class PhotoPermissionActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
      startAuthorization()
    } else if (savedInstanceState == null) {
      requestPermissions(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        1,
      )
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 1 &&
      checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    ) {
      PhotoEnrollmentWorker.enqueue(this)
    }
    if (requestCode == 1) startAuthorization()
  }

  private fun startAuthorization() {
    startActivity(Intent(this, MealRelayAuthorizationActivity::class.java))
    finish()
  }
}
