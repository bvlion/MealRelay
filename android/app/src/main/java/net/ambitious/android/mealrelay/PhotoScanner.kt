package net.ambitious.android.mealrelay

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import net.ambitious.android.mealrelay.data.photo.PhotoProcessingDao
import net.ambitious.android.mealrelay.data.photo.PhotoResultEntity

class PhotoScanner(
  private val context: Context,
  private val photoProcessingDao: PhotoProcessingDao,
  private val submitFoodPhoto: (Uri, Long) -> Unit = { _, _ -> },
  private val createClassifier: () -> (Uri) -> Boolean,
) {
  fun scan(shouldStop: () -> Boolean): Boolean {
    if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
      return true
    }
    val scanState = photoProcessingDao.getScanState() ?: return false
    val currentVersion: String? = MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
    if (currentVersion == null) return false
    val scanGeneration = MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val isResynchronizing = scanState.version != currentVersion
    val generation = if (isResynchronizing) 0 else scanState.generation
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val cameraPackage = context.packageManager.resolveActivity(
      Intent(MediaStore.ACTION_IMAGE_CAPTURE),
      PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName
    val cursor = requireNotNull(context.contentResolver.query(
      collection,
      arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.GENERATION_ADDED,
        MediaStore.Images.Media.GENERATION_MODIFIED,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.OWNER_PACKAGE_NAME,
      ),
      "${MediaStore.Images.Media.GENERATION_MODIFIED} > ? AND " +
        "${MediaStore.Images.Media.GENERATION_MODIFIED} <= ?",
      arrayOf(generation.toString(), scanGeneration.toString()),
      "${MediaStore.Images.Media.GENERATION_MODIFIED} ASC, ${MediaStore.Images.Media._ID} ASC",
    )) { "MediaStore query returned no cursor" }
    var classifyPhoto: ((Uri) -> Boolean)? = null
    cursor.use {
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
      val addedGenerationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.GENERATION_ADDED)
      val capturedAtColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
      val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
      val ownerColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.OWNER_PACKAGE_NAME)
      while (cursor.moveToNext()) {
        if (shouldStop()) return false
        val capturedAt = cursor.getLong(capturedAtColumn)
        val ownerPackage = cursor.getString(ownerColumn)
        val isCameraPhoto = (cameraPackage != null && ownerPackage == cameraPackage) ||
          (ownerPackage == null && cursor.getString(pathColumn) == "DCIM/Camera/")
        val wasAddedAfterEnrollment = cursor.getLong(addedGenerationColumn) > scanState.enrolledGeneration
        if (!isCameraPhoto ||
          (isResynchronizing && capturedAt < scanState.enrolledAt) ||
          (!isResynchronizing && !wasAddedAfterEnrollment)
        ) {
          continue
        }
        if (capturedAt <= 0) {
          Log.w("PhotoScanner", "camera photo has no capture time")
          continue
        }
        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
        if (photoProcessingDao.hasResult(currentVersion, uri.toString())) {
          continue
        }
        val classifier = classifyPhoto ?: createClassifier().also { classifyPhoto = it }
        val isFood = try {
          classifier(uri)
        } catch (exception: Exception) {
          Log.w("PhotoScanner", "camera photo classification failed", exception)
          null
        }
        if (isFood == true) {
          submitFoodPhoto(uri, capturedAt)
        }
        photoProcessingDao.insertResult(PhotoResultEntity(currentVersion, uri.toString(), capturedAt, isFood))
        if (isFood != null) {
          Log.i("PhotoScanner", "camera photo classified isFood=$isFood")
        }
      }
    }
    if (shouldStop() || MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL_PRIMARY) != currentVersion) {
      return false
    }
    photoProcessingDao.updateScanState(scanState.copy(
      version = currentVersion,
      generation = scanGeneration,
      enrolledGeneration = if (isResynchronizing) scanGeneration else scanState.enrolledGeneration,
    ))
    return true
  }
}
