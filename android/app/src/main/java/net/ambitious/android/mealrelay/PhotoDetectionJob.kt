package net.ambitious.android.mealrelay

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class PhotoDetectionJob : JobService() {
  private val stoppedJobs = ConcurrentHashMap<Int, AtomicBoolean>()

  override fun onStartJob(params: JobParameters): Boolean {
    val isStopped = AtomicBoolean(false)
    stoppedJobs[params.jobId] = isStopped
    Thread {
      try {
        synchronized(PhotoDetectionJob::class.java) {
          if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
            val preferences = getSharedPreferences("photo_detection", MODE_PRIVATE)
            val currentVersion = MediaStore.getVersion(this, MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val previousVersion = preferences.getString("version", null)
            var generation = preferences.getLong("generation", -1)
            if (generation < 0) {
              generation = MediaStore.getGeneration(this, MediaStore.VOLUME_EXTERNAL_PRIMARY)
              preferences.edit().putString("version", currentVersion).putLong("generation", generation)
                .putLong("enrolled_at", System.currentTimeMillis()).commit()
            } else {
              if (previousVersion != currentVersion) {
                generation = 0
              }
              val scanGeneration = MediaStore.getGeneration(this, MediaStore.VOLUME_EXTERNAL_PRIMARY)
              val database = openOrCreateDatabase("photo_results.db", MODE_PRIVATE, null)
              try {
                database.execSQL(
                  "CREATE TABLE IF NOT EXISTS photo_results (uri TEXT PRIMARY KEY, captured_at INTEGER NOT NULL, is_food INTEGER NOT NULL)",
                )
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val cameraPackage = packageManager.resolveActivity(
                  Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                  PackageManager.MATCH_DEFAULT_ONLY,
                )?.activityInfo?.packageName
                contentResolver.query(
                  collection,
                  arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.GENERATION_ADDED,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.OWNER_PACKAGE_NAME,
                  ),
                  "${MediaStore.Images.Media.GENERATION_ADDED} > ?",
                  arrayOf(generation.toString()),
                  "${MediaStore.Images.Media.GENERATION_ADDED} ASC, ${MediaStore.Images.Media._ID} ASC",
                )?.use { cursor ->
                  val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                  val generationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.GENERATION_ADDED)
                  val capturedAtColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                  val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                  val ownerColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.OWNER_PACKAGE_NAME)
                  FoodClassifier(this).use { classifier ->
                    while (!isStopped.get() && cursor.moveToNext()) {
                      val mediaGeneration = cursor.getLong(generationColumn)
                      val capturedAt = cursor.getLong(capturedAtColumn)
                      val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                      val ownerPackage = cursor.getString(ownerColumn)
                      val isCameraPhoto = (cameraPackage != null && ownerPackage == cameraPackage) ||
                        (ownerPackage == null && cursor.getString(pathColumn) == "DCIM/Camera/")
                      if (!isCameraPhoto || (previousVersion != currentVersion &&
                          capturedAt < preferences.getLong("enrolled_at", Long.MAX_VALUE))) {
                        generation = mediaGeneration
                        preferences.edit().putString("version", currentVersion).putLong("generation", generation).commit()
                        continue
                      }
                      val alreadyProcessed = database.rawQuery(
                        "SELECT 1 FROM photo_results WHERE uri = ?",
                        arrayOf(uri.toString()),
                      ).use { it.moveToFirst() }
                      if (!alreadyProcessed && capturedAt > 0) {
                        val source = ImageDecoder.createSource(contentResolver, uri)
                        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                          val scale = minOf(1f, 224f / maxOf(info.size.width, info.size.height))
                          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                          decoder.setTargetSize(
                            maxOf(1, (info.size.width * scale).roundToInt()),
                            maxOf(1, (info.size.height * scale).roundToInt()),
                          )
                        }
                        try {
                          val isFood = classifier.isFood(bitmap)
                          database.execSQL(
                            "INSERT OR IGNORE INTO photo_results (uri, captured_at, is_food) VALUES (?, ?, ?)",
                            arrayOf<Any>(uri.toString(), capturedAt, if (isFood) 1 else 0),
                          )
                          Log.i("PhotoDetectionJob", "camera photo classified isFood=$isFood")
                        } finally {
                          bitmap.recycle()
                        }
                      } else if (capturedAt <= 0) {
                        Log.w("PhotoDetectionJob", "camera photo has no capture time")
                      }
                      generation = mediaGeneration
                      preferences.edit().putString("version", currentVersion).putLong("generation", generation).commit()
                    }
                  }
                }
                if (previousVersion != currentVersion && !isStopped.get()) {
                  preferences.edit().putString("version", currentVersion)
                    .putLong("generation", maxOf(generation, scanGeneration))
                    .commit()
                }
              } finally {
                database.close()
              }
            }
          }
        }
        if (!isStopped.get() && params.jobId == CONTENT_JOB_ID) {
          val scheduler = getSystemService(JobScheduler::class.java)
          val scheduled = scheduler.schedule(
            JobInfo.Builder(CONTENT_JOB_ID, ComponentName(this, PhotoDetectionJob::class.java))
              .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                  MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                  JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                ),
              )
              .build(),
          )
          if (scheduled != JobScheduler.RESULT_SUCCESS) {
            Log.e("PhotoDetectionJob", "could not resume photo monitoring")
            jobFinished(params, true)
          }
        } else if (!isStopped.get()) {
          jobFinished(params, false)
        }
      } catch (exception: Exception) {
        Log.e("PhotoDetectionJob", "photo detection failed", exception)
        if (!isStopped.get()) {
          jobFinished(params, true)
        }
      } finally {
        stoppedJobs.remove(params.jobId, isStopped)
      }
    }.start()
    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    stoppedJobs[params.jobId]?.set(true)
    return true
  }

  companion object {
    const val CONTENT_JOB_ID = 2
    const val SCAN_JOB_ID = 3
  }
}
