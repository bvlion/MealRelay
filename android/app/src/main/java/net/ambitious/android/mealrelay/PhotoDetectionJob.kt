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

class PhotoDetectionJob : JobService() {
  private val stoppedJobs = ConcurrentHashMap<Int, AtomicBoolean>()
  internal var foodClassifier: FoodClassifier? = null

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
                  "CREATE TABLE IF NOT EXISTS photo_results (version TEXT NOT NULL, uri TEXT NOT NULL, " +
                    "captured_at INTEGER NOT NULL, is_food INTEGER, PRIMARY KEY (version, uri))",
                )
                val hasVersion = database.rawQuery("PRAGMA table_info(photo_results)", null).use { columns ->
                  val nameColumn = columns.getColumnIndexOrThrow("name")
                  var found = false
                  while (columns.moveToNext()) {
                    if (columns.getString(nameColumn) == "version") {
                      found = true
                    }
                  }
                  found
                }
                if (!hasVersion) {
                  database.beginTransaction()
                  try {
                    database.execSQL("ALTER TABLE photo_results RENAME TO photo_results_before_version")
                    database.execSQL(
                      "CREATE TABLE photo_results (version TEXT NOT NULL, uri TEXT NOT NULL, " +
                        "captured_at INTEGER NOT NULL, is_food INTEGER, PRIMARY KEY (version, uri))",
                    )
                    database.execSQL(
                      "INSERT INTO photo_results (version, uri, captured_at, is_food) " +
                        "SELECT ?, uri, captured_at, is_food FROM photo_results_before_version",
                      arrayOf(previousVersion ?: currentVersion),
                    )
                    database.execSQL("DROP TABLE photo_results_before_version")
                    database.setTransactionSuccessful()
                  } finally {
                    database.endTransaction()
                  }
                }
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
                  (foodClassifier ?: FoodClassifier(this)).use { classifier ->
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
                        "SELECT 1 FROM photo_results WHERE version = ? AND uri = ?",
                        arrayOf(currentVersion, uri.toString()),
                      ).use { it.moveToFirst() }
                      if (!alreadyProcessed && capturedAt > 0) {
                        var isFood: Boolean? = null
                        try {
                          val source = ImageDecoder.createSource(contentResolver, uri)
                          val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                          }
                          try {
                            isFood = classifier.isFood(bitmap)
                          } finally {
                            bitmap.recycle()
                          }
                        } catch (exception: Exception) {
                          Log.w("PhotoDetectionJob", "camera photo classification failed", exception)
                        }
                        database.execSQL(
                          "INSERT OR IGNORE INTO photo_results (version, uri, captured_at, is_food) VALUES (?, ?, ?, ?)",
                          arrayOf<Any?>(currentVersion, uri.toString(), capturedAt, isFood?.let { if (it) 1 else 0 }),
                        )
                        if (isFood != null) {
                          Log.i("PhotoDetectionJob", "camera photo classified isFood=$isFood")
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
