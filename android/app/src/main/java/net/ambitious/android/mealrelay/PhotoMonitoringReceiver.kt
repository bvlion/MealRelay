package net.ambitious.android.mealrelay

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

class PhotoMonitoringReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
      return
    }
    if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
      return
    }
    val scheduler = context.getSystemService(JobScheduler::class.java)
    scheduler.schedule(
      JobInfo.Builder(PhotoDetectionJob.CONTENT_JOB_ID, ComponentName(context, PhotoDetectionJob::class.java))
        .addTriggerContentUri(
          JobInfo.TriggerContentUri(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
          ),
        )
        .build(),
    )
    scheduler.schedule(
      JobInfo.Builder(PhotoDetectionJob.SCAN_JOB_ID, ComponentName(context, PhotoDetectionJob::class.java))
        .build(),
    )
  }
}
