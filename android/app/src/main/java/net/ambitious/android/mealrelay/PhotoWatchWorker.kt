package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager

class PhotoWatchWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
  override fun doWork(): Result {
    return try {
      if (applicationContext.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
        PackageManager.PERMISSION_GRANTED
      ) {
        return Result.success()
      }
      enqueue(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE).result.get()
      PhotoScanWorker.enqueue(applicationContext).result.get()
      Result.success()
    } catch (exception: Exception) {
      Log.e("PhotoWatchWorker", "could not resume photo monitoring", exception)
      Result.retry()
    }
  }

  companion object {
    fun enqueue(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) =
      WorkManager.getInstance(context).enqueueUniqueWork(
        "photo_watch",
        policy,
        OneTimeWorkRequest.Builder(PhotoWatchWorker::class.java)
          .setConstraints(
            Constraints.Builder()
              .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
              .build(),
          )
          .build(),
      )
  }
}
