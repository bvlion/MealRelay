package net.ambitious.android.mealrelay

import android.Manifest
import android.app.Application
import android.app.job.JobParameters
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], shadows = [PhotoDetectionJobTest.ShadowImageDecoder::class])
class PhotoDetectionJobTest {
  @Test
  fun decodeAndClassificationFailuresDoNotStopLaterPhotos() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val invalidPhoto = File.createTempFile("invalid-photo", ".jpg", application.cacheDir)
    invalidPhoto.writeText("invalid image")
    val validPhoto = File("src/androidTest/assets/food.jpg")
    assertTrue(validPhoto.isFile)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.photos = listOf(
      Photo(1, 1, 1000, invalidPhoto),
      Photo(2, 2, 2000, validPhoto),
      Photo(3, 3, 3000, validPhoto),
    )
    provider.generation = 3
    ShadowImageDecoder.failedDecodesRemaining = AtomicInteger(1)
    application.getSharedPreferences("photo_detection", 0).edit()
      .putString("version", provider.version).putLong("generation", 0).putLong("enrolled_at", 1)
      .commit()

    val job = Robolectric.setupService(PhotoDetectionJob::class.java)
    val classifier = Mockito.mock(FoodClassifier::class.java)
    val classificationCalls = AtomicInteger()
    val failuresRemaining = AtomicInteger(1)
    Mockito.doAnswer { invocation ->
      classificationCalls.incrementAndGet()
      if (failuresRemaining.getAndDecrement() > 0) {
        throw IllegalStateException("test classification failure")
      }
      val bitmap = invocation.getArgument<Bitmap>(0)
      assertEquals(382, bitmap.width)
      assertEquals(512, bitmap.height)
      true
    }.`when`(classifier).isFood(
      Mockito.any(Bitmap::class.java) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    )
    job.foodClassifier = classifier
    val parameters = Mockito.mock(JobParameters::class.java)
    Mockito.`when`(parameters.jobId).thenReturn(PhotoDetectionJob.CONTENT_JOB_ID)
    job.onStartJob(parameters)
    val preferences = application.getSharedPreferences("photo_detection", 0)
    val deadline = System.nanoTime() + 10_000_000_000L
    while (preferences.getLong("generation", 0) < 3 && System.nanoTime() < deadline) {
      Thread.sleep(10)
    }
    assertEquals(3L, preferences.getLong("generation", 0))

    application.openOrCreateDatabase("photo_results.db", 0, null).use { database ->
      database.rawQuery("SELECT uri, is_food FROM photo_results ORDER BY uri", null).use { cursor ->
        assertEquals(3, cursor.count)
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.isNull(1))
        assertTrue(cursor.moveToNext())
        assertTrue(cursor.isNull(1))
        assertTrue(cursor.moveToNext())
        assertEquals(1, cursor.getInt(1))
      }
    }
    assertEquals(2, classificationCalls.get())
  }

  @Test
  fun versionChangeProcessesReusedMediaStoreId() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val validPhoto = File("src/androidTest/assets/food.jpg")
    assertTrue(validPhoto.isFile)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.photos = listOf(Photo(7, 5, 1000, validPhoto))
    provider.generation = 5
    ShadowImageDecoder.failedDecodesRemaining = AtomicInteger(0)
    val preferences = application.getSharedPreferences("photo_detection", 0)
    preferences.edit().putString("version", provider.version).putLong("generation", 0)
      .putLong("enrolled_at", 1500).commit()
    val parameters = Mockito.mock(JobParameters::class.java)
    Mockito.`when`(parameters.jobId).thenReturn(PhotoDetectionJob.CONTENT_JOB_ID)
    val job = Robolectric.setupService(PhotoDetectionJob::class.java)
    val classifier = Mockito.mock(FoodClassifier::class.java)
    val classificationCalls = AtomicInteger()
    val resynchronizationInterrupted = CountDownLatch(1)
    Mockito.doAnswer {
      if (classificationCalls.incrementAndGet() == 2) {
        job.onStopJob(parameters)
        resynchronizationInterrupted.countDown()
      }
      true
    }.`when`(classifier).isFood(
      Mockito.any(Bitmap::class.java) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    )
    job.foodClassifier = classifier

    job.onStartJob(parameters)
    var deadline = System.nanoTime() + 10_000_000_000L
    while (preferences.getLong("generation", 0) < 5 && System.nanoTime() < deadline) {
      Thread.sleep(10)
    }
    assertEquals(5L, preferences.getLong("generation", 0))
    provider.version = "version-2"
    provider.generation = 2
    provider.photos = listOf(
      Photo(7, 1, 2000, validPhoto),
      Photo(8, 2, 1000, validPhoto),
    )

    job.onStartJob(parameters)
    assertTrue(resynchronizationInterrupted.await(5, TimeUnit.SECONDS))
    job.onStartJob(parameters)
    deadline = System.nanoTime() + 10_000_000_000L
    while (preferences.getString("version", null) != "version-2" && System.nanoTime() < deadline) {
      Thread.sleep(10)
    }
    assertEquals("version-2", preferences.getString("version", null))
    application.openOrCreateDatabase("photo_results.db", 0, null).use { database ->
      database.rawQuery(
        "SELECT version, uri, captured_at FROM photo_results ORDER BY version",
        null,
      ).use { cursor ->
        assertEquals(2, cursor.count)
        assertTrue(cursor.moveToFirst())
        assertEquals("version-1", cursor.getString(0))
        assertEquals(1000L, cursor.getLong(2))
        val reusedUri = cursor.getString(1)
        assertTrue(cursor.moveToNext())
        assertEquals("version-2", cursor.getString(0))
        assertEquals(reusedUri, cursor.getString(1))
        assertEquals(2000L, cursor.getLong(2))
      }
    }
    assertEquals(2, classificationCalls.get())
  }

  @Test
  fun legacyResultIsAssignedToPreviousVersionDuringResynchronization() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.version = "version-2"
    provider.generation = 2
    provider.photos = listOf(Photo(7, 1, 2000, File("src/androidTest/assets/food.jpg")))
    ShadowImageDecoder.failedDecodesRemaining = AtomicInteger(0)
    val preferences = application.getSharedPreferences("photo_detection", 0)
    preferences.edit().putString("version", "version-1").putLong("generation", 5)
      .putLong("enrolled_at", 1).commit()
    val reusedUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 7)
    application.openOrCreateDatabase("photo_results.db", 0, null).use { database ->
      database.execSQL(
        "CREATE TABLE photo_results (uri TEXT PRIMARY KEY, captured_at INTEGER NOT NULL, is_food INTEGER NOT NULL)",
      )
      database.execSQL(
        "INSERT INTO photo_results (uri, captured_at, is_food) VALUES (?, ?, ?)",
        arrayOf<Any>(reusedUri.toString(), 1000L, 0),
      )
    }
    val job = Robolectric.setupService(PhotoDetectionJob::class.java)
    val classifier = Mockito.mock(FoodClassifier::class.java)
    Mockito.doReturn(true).`when`(classifier).isFood(
      Mockito.any(Bitmap::class.java) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    )
    job.foodClassifier = classifier
    val parameters = Mockito.mock(JobParameters::class.java)
    Mockito.`when`(parameters.jobId).thenReturn(PhotoDetectionJob.CONTENT_JOB_ID)

    job.onStartJob(parameters)
    val deadline = System.nanoTime() + 10_000_000_000L
    while (preferences.getString("version", null) != "version-2" && System.nanoTime() < deadline) {
      Thread.sleep(10)
    }
    assertEquals("version-2", preferences.getString("version", null))
    application.openOrCreateDatabase("photo_results.db", 0, null).use { database ->
      database.rawQuery(
        "SELECT version, uri, captured_at, is_food FROM photo_results ORDER BY version",
        null,
      ).use { cursor ->
        assertEquals(2, cursor.count)
        assertTrue(cursor.moveToFirst())
        assertEquals("version-1", cursor.getString(0))
        assertEquals(reusedUri.toString(), cursor.getString(1))
        assertEquals(1000L, cursor.getLong(2))
        assertEquals(0, cursor.getInt(3))
        assertTrue(cursor.moveToNext())
        assertEquals("version-2", cursor.getString(0))
        assertEquals(reusedUri.toString(), cursor.getString(1))
        assertEquals(2000L, cursor.getLong(2))
        assertEquals(1, cursor.getInt(3))
      }
    }
    Mockito.verify(classifier).isFood(
      Mockito.any(Bitmap::class.java) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    )
  }

  data class Photo(val id: Long, val generation: Long, val capturedAt: Long, val file: File)

  class PhotoMediaProvider : ContentProvider() {
    var version = "version-1"
    var generation = 0L
    var photos = emptyList<Photo>()

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = Bundle().apply {
      when (method) {
        "get_version" -> putString(Intent.EXTRA_TEXT, version)
        "get_generation" -> putLong(Intent.EXTRA_INDEX, generation)
      }
    }

    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
    ): Cursor {
      val columns = requireNotNull(projection)
      val cursor = MatrixCursor(columns)
      val afterGeneration = selectionArgs?.single()?.toLong() ?: 0
      for (photo in photos.sortedWith(compareBy(Photo::generation, Photo::id))) {
        if (photo.generation <= afterGeneration) continue
        cursor.addRow(columns.map { column ->
          when (column) {
            MediaStore.Images.Media._ID -> photo.id
            MediaStore.Images.Media.GENERATION_ADDED -> photo.generation
            MediaStore.Images.Media.DATE_TAKEN -> photo.capturedAt
            MediaStore.Images.Media.RELATIVE_PATH -> "DCIM/Camera/"
            MediaStore.Images.Media.OWNER_PACKAGE_NAME -> null
            else -> error("Unexpected column: $column")
          }
        })
      }
      return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
      val file = photos.single { it.id == ContentUris.parseId(uri) }.file
      return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
    ): Int = 0
  }

  @Implements(ImageDecoder::class)
  class ShadowImageDecoder {
    companion object {
      var failedDecodesRemaining = AtomicInteger()

      @JvmStatic
      @Implementation
      fun decodeBitmap(
        source: ImageDecoder.Source,
        listener: ImageDecoder.OnHeaderDecodedListener,
      ): Bitmap {
        if (failedDecodesRemaining.getAndDecrement() > 0) {
          throw IllegalStateException("test decode failure")
        }
        return Bitmap.createBitmap(382, 512, Bitmap.Config.ARGB_8888)
      }
    }
  }
}
