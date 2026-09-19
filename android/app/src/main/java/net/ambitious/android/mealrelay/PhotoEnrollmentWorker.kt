package net.ambitious.android.mealrelay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager

class PhotoEnrollmentWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
  override fun doWork(): Result {
    return try {
      if (applicationContext.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
        PackageManager.PERMISSION_GRANTED
      ) {
        synchronized(PhotoScanner::class.java) {
          PhotoProcessingDatabase.get(applicationContext).photoProcessingDao().markFullAccessLost()
        }
        return Result.success()
      }
      val currentVersion: String? = MediaStore.getVersion(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY)
      if (currentVersion == null) return Result.retry()
      val version = inputData.getString("version")
      val generation = inputData.getLong("generation", -1).takeIf { it >= 0 }
        ?: 0
      val enrolledAt = inputData.getLong("enrolled_at", System.currentTimeMillis())
      val photoProcessingDao = PhotoProcessingDatabase.get(applicationContext).photoProcessingDao()
      synchronized(PhotoScanner::class.java) {
        val scanState = photoProcessingDao.getScanState()
        val newState = PhotoScanStateEntity(
          version = version,
          generation = generation,
          enrolledGeneration = generation,
          enrolledAt = enrolledAt,
        )
        if (scanState == null) {
          photoProcessingDao.insertScanState(newState)
        } else if (inputData.getBoolean("should_reset", false) || !scanState.hasFullAccess) {
          photoProcessingDao.updateScanState(newState)
        }
      }
      PhotoWatchWorker.enqueue(applicationContext).result.get()
      PhotoScanWorker.enqueuePeriodic(applicationContext).result.get()
      PhotoScanWorker.enqueue(applicationContext).result.get()
      Result.success()
    } catch (exception: Exception) {
      Log.e("PhotoEnrollmentWorker", "photo enrollment failed", exception)
      Result.retry()
    }
  }

  companion object {
    fun enqueue(context: Context, shouldReset: Boolean = false) {
      val data = Data.Builder()
        .putLong("enrolled_at", System.currentTimeMillis())
        .putBoolean("should_reset", shouldReset)
      val version: String? = MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
      if (version != null) {
        data.putString("version", version)
        data.putLong("generation", MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY))
      }
      WorkManager.getInstance(context).enqueueUniqueWork(
        "photo_enrollment",
        if (shouldReset) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
        OneTimeWorkRequest.Builder(PhotoEnrollmentWorker::class.java)
          .setInputData(data.build())
          .build(),
      )
    }
  }
}
