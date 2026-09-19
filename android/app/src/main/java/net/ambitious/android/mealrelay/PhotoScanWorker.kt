package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PhotoScanWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
  override fun doWork(): Result = try {
    val isComplete = synchronized(PhotoScanner::class.java) {
      PhotoClassification(applicationContext).use { classification ->
        PhotoScanner(
          applicationContext,
          PhotoProcessingDatabase.get(applicationContext).photoProcessingDao(),
          classification::prepareClassifier,
        ).scan { isStopped }
      }
    }
    if (isComplete && applicationContext.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
      PackageManager.PERMISSION_GRANTED
    ) {
      PhotoWatchWorker.enqueue(applicationContext).result.get()
    }
    if (isComplete) Result.success() else Result.retry()
  } catch (exception: Exception) {
    Log.e("PhotoScanWorker", "photo detection failed", exception)
    Result.retry()
  }

  companion object {
    fun enqueue(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
      "photo_scan",
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      OneTimeWorkRequest.Builder(PhotoScanWorker::class.java).build(),
    )

    fun enqueuePeriodic(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      "photo_scan_periodic",
      ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequest.Builder(PhotoScanWorker::class.java, 15, TimeUnit.MINUTES).build(),
    )
  }
}
