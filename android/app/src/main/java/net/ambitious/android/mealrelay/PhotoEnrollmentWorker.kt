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
import net.ambitious.android.mealrelay.data.photo.PhotoScanStateEntity
import net.ambitious.android.mealrelay.data.photo.PhotoProcessingDao
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PhotoEnrollmentWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted parameters: WorkerParameters,
  private val photoProcessingDao: PhotoProcessingDao,
) : Worker(context, parameters) {
  override fun doWork(): Result {
    return try {
      if (applicationContext.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
        PackageManager.PERMISSION_GRANTED
      ) {
        return Result.success()
      }
      val currentVersion: String? = MediaStore.getVersion(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY)
      if (currentVersion == null) return Result.retry()
      val version = inputData.getString("version")
      val generation = inputData.getLong("generation", -1).takeIf { it >= 0 }
        ?: 0
      val enrolledAt = inputData.getLong("enrolled_at", System.currentTimeMillis())
      synchronized(PhotoScanner::class.java) {
        photoProcessingDao.insertScanState(
          PhotoScanStateEntity(
            version = version,
            generation = generation,
            enrolledGeneration = generation,
            enrolledAt = enrolledAt,
          ),
        )
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
    fun enqueue(context: Context) {
      val data = Data.Builder()
        .putLong("enrolled_at", System.currentTimeMillis())
      val version: String? = MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
      if (version != null) {
        data.putString("version", version)
        data.putLong("generation", MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY))
      }
      WorkManager.getInstance(context).enqueueUniqueWork(
        "photo_enrollment",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequest.Builder(PhotoEnrollmentWorker::class.java)
          .setInputData(data.build())
          .build(),
      )
    }
  }
}
