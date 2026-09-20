package net.ambitious.android.mealrelay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class PhotoBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
      return
    }
    PhotoWatchWorker.enqueue(context)
    PhotoScanWorker.enqueuePeriodic(context)
    PhotoScanWorker.enqueue(context)
  }
}
