package net.ambitious.android.mealrelay

import android.Manifest
import android.app.Activity
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore

class PhotoPermissionActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      val preferences = getSharedPreferences("photo_detection", MODE_PRIVATE)
      if (!preferences.contains("generation")) {
        preferences.edit()
          .putString("version", MediaStore.getVersion(this, MediaStore.VOLUME_EXTERNAL_PRIMARY))
          .putLong("generation", MediaStore.getGeneration(this, MediaStore.VOLUME_EXTERNAL_PRIMARY))
          .putLong("enrolled_at", System.currentTimeMillis())
          .apply()
      }
      val scheduler = getSystemService(JobScheduler::class.java)
      if (scheduler.getPendingJob(PhotoDetectionJob.CONTENT_JOB_ID) == null) {
        scheduler.schedule(
          JobInfo.Builder(PhotoDetectionJob.CONTENT_JOB_ID, ComponentName(this, PhotoDetectionJob::class.java))
            .addTriggerContentUri(
              JobInfo.TriggerContentUri(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
              ),
            )
            .build(),
        )
        scheduler.schedule(
          JobInfo.Builder(PhotoDetectionJob.SCAN_JOB_ID, ComponentName(this, PhotoDetectionJob::class.java))
            .build(),
        )
      }
      finish()
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
      val preferences = getSharedPreferences("photo_detection", MODE_PRIVATE)
      if (!preferences.contains("generation")) {
        preferences.edit()
          .putString("version", MediaStore.getVersion(this, MediaStore.VOLUME_EXTERNAL_PRIMARY))
          .putLong("generation", MediaStore.getGeneration(this, MediaStore.VOLUME_EXTERNAL_PRIMARY))
          .putLong("enrolled_at", System.currentTimeMillis())
          .apply()
      }
      val scheduler = getSystemService(JobScheduler::class.java)
      scheduler.schedule(
        JobInfo.Builder(PhotoDetectionJob.CONTENT_JOB_ID, ComponentName(this, PhotoDetectionJob::class.java))
          .addTriggerContentUri(
            JobInfo.TriggerContentUri(
              MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
              JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
            ),
          )
          .build(),
      )
      scheduler.schedule(
        JobInfo.Builder(PhotoDetectionJob.SCAN_JOB_ID, ComponentName(this, PhotoDetectionJob::class.java))
          .build(),
      )
    }
    finish()
  }
}
