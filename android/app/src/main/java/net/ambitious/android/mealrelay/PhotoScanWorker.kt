package net.ambitious.android.mealrelay

import android.content.Context
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
      var classification: PhotoClassification? = null
      try {
        PhotoScanner(
          applicationContext,
          PhotoProcessingDatabase.get(applicationContext).photoProcessingDao(),
        ) {
          val classifier = PhotoClassification(applicationContext)
          classification = classifier
          classifier::isFood
        }.scan { isStopped }
      } finally {
        classification?.close()
      }
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
