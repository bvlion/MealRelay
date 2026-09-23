package net.ambitious.android.mealrelay

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.room.Room
import net.ambitious.android.mealrelay.data.database.MealRelayDatabase
import net.ambitious.android.mealrelay.data.photo.PhotoResultEntity
import net.ambitious.android.mealrelay.data.photo.PhotoScanStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class PhotoScannerTest {
  @Test
  fun decodeAndClassificationFailuresDoNotStopLaterPhotos() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.photos = listOf(
      Photo(1, 1, 1000),
      Photo(2, 2, 2000),
      Photo(3, 3, 3000),
    )
    provider.generation = 3
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = provider.version, generation = 0, enrolledAt = 1))
      val classifiedIds = mutableListOf<Long>()
      var classifierCreations = 0
      val classify: (Uri) -> Boolean = { uri ->
        val id = ContentUris.parseId(uri)
        classifiedIds.add(id)
        when (id) {
          1L -> throw IllegalArgumentException("test decode failure")
          2L -> throw IllegalStateException("test classification failure")
          else -> true
        }
      }
      val scanner = PhotoScanner(application, dao) {
        classifierCreations++
        classify
      }

      assertTrue(scanner.scan { false })
      assertEquals(listOf(1L, 2L, 3L), classifiedIds)
      assertEquals(1, classifierCreations)
      val results = dao.getResults()
      assertEquals(3, results.size)
      assertNull(results[0].isFood)
      assertNull(results[1].isFood)
      assertEquals(true, results[2].isFood)
      assertEquals(3000L, results[2].capturedAt)
      assertEquals(3L, dao.getScanState()?.generation)
    } finally {
      database.close()
    }
  }

  @Test
  fun interruptedVersionRescanKeepsEnrollmentFilterAndRecognizesReusedUri() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.version = "version-2"
    provider.generation = 3
    provider.photos = listOf(
      Photo(7, 1, 2000),
      Photo(8, 2, 1000),
      Photo(9, 3, 3000),
    )
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = "version-1", generation = 5, enrolledAt = 1500))
      val reusedUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 7).toString()
      dao.insertResult(PhotoResultEntity("version-1", reusedUri, 1000, false))
      val classifiedIds = mutableListOf<Long>()
      var isStopped = false
      val classify: (Uri) -> Boolean = { uri ->
        val id = ContentUris.parseId(uri)
        classifiedIds.add(id)
        if (id == 7L) isStopped = true
        true
      }
      val scanner = PhotoScanner(application, dao) { classify }

      assertFalse(scanner.scan { isStopped })
      assertEquals("version-1", dao.getScanState()?.version)
      assertEquals(5L, dao.getScanState()?.generation)
      isStopped = false
      assertTrue(scanner.scan { isStopped })
      assertEquals(listOf(7L, 9L), classifiedIds)
      assertEquals("version-2", dao.getScanState()?.version)
      assertEquals(3L, dao.getScanState()?.generation)
      val results = dao.getResults()
      assertEquals(3, results.size)
      assertEquals("version-1", results[0].version)
      assertEquals("version-2", results[1].version)
      assertEquals(reusedUri, results[1].uri)
      assertEquals(2000L, results[1].capturedAt)
      assertFalse(results.any { it.uri.endsWith("/8") })

      provider.generation = 4
      provider.photos = listOf(
        Photo(7, 1, 2000),
        Photo(id = 8, addedGeneration = 2, modifiedGeneration = 4, capturedAt = 1000),
        Photo(9, 3, 3000),
        Photo(10, 4, 900),
      )
      assertTrue(scanner.scan { false })
      assertEquals(listOf(7L, 9L, 10L), classifiedIds)
      assertFalse(dao.getResults().any { it.uri.endsWith("/8") })
    } finally {
      database.close()
    }
  }

  @Test
  fun photoPublishedAfterEarlierCheckpointIsFoundByModifiedGeneration() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.generation = 2
    provider.photos = listOf(Photo(2, 2, 2000))
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = provider.version, generation = 0, enrolledAt = 1500))
      val classifiedIds = mutableListOf<Long>()
      val classify: (Uri) -> Boolean = { uri ->
        classifiedIds.add(ContentUris.parseId(uri))
        true
      }
      val scanner = PhotoScanner(application, dao) { classify }
      assertTrue(scanner.scan { false })
      assertEquals(2L, dao.getScanState()?.generation)

      provider.generation = 3
      provider.photos = listOf(Photo(2, 2, 2000), Photo(1, 3, 2200))
      assertTrue(scanner.scan { false })
      assertEquals(listOf(2L, 1L), classifiedIds)
      assertEquals(3L, dao.getScanState()?.generation)
    } finally {
      database.close()
    }
  }

  @Test
  fun sameVersionScanAcceptsNewGenerationWithEarlierCaptureTime() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.generation = 6
    provider.photos = listOf(Photo(4, 6, 1000))
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = provider.version, generation = 5, enrolledAt = 1500))
      val classifiedIds = mutableListOf<Long>()
      val classify: (Uri) -> Boolean = { uri ->
        classifiedIds.add(ContentUris.parseId(uri))
        true
      }
      assertTrue(PhotoScanner(application, dao) { classify }.scan { false })
      assertEquals(listOf(4L), classifiedIds)
      assertEquals(1000L, dao.getResults().single().capturedAt)
      assertEquals(6L, dao.getScanState()?.generation)
    } finally {
      database.close()
    }
  }

  @Test
  fun sameVersionScanSkipsPreEnrollmentPhotoModifiedLater() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.generation = 7
    provider.photos = listOf(
      Photo(id = 1, addedGeneration = 1, modifiedGeneration = 6, capturedAt = 1000),
      Photo(id = 2, addedGeneration = 6, modifiedGeneration = 7, capturedAt = 1000),
    )
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(
        PhotoScanStateEntity(
          version = provider.version,
          generation = 5,
          enrolledGeneration = 5,
          enrolledAt = 1500,
        ),
      )
      val classifiedIds = mutableListOf<Long>()
      val classify: (Uri) -> Boolean = { uri ->
        classifiedIds.add(ContentUris.parseId(uri))
        true
      }

      assertTrue(PhotoScanner(application, dao) { classify }.scan { false })
      assertEquals(listOf(2L), classifiedIds)
      assertEquals(7L, dao.getScanState()?.generation)
    } finally {
      database.close()
    }
  }

  @Test
  fun enrollmentWithoutVersionRescansFromZeroAfterVolumeReturns() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.generation = 2
    provider.photos = listOf(Photo(1, 1, 1000), Photo(2, 2, 2000))
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = null, generation = 0, enrolledAt = 1500))
      val classifiedIds = mutableListOf<Long>()
      val classify: (Uri) -> Boolean = { uri ->
        classifiedIds.add(ContentUris.parseId(uri))
        true
      }
      assertTrue(PhotoScanner(application, dao) { classify }.scan { false })
      assertEquals(listOf(2L), classifiedIds)
      assertEquals(provider.version, dao.getScanState()?.version)
      assertEquals(2L, dao.getScanState()?.generation)
    } finally {
      database.close()
    }
  }

  @Test
  fun noCandidateOrUnavailableVolumeOrPermissionDoesNotCreateClassifier() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = provider.version, generation = 0, enrolledAt = 1))
      var classifierCreations = 0
      val classify: (Uri) -> Boolean = { true }
      val scanner = PhotoScanner(application, dao) {
        classifierCreations++
        classify
      }
      assertTrue(scanner.scan { false })
      assertEquals(0, classifierCreations)

      provider.generation = 1
      provider.photos = listOf(Photo(1, 1, 1000))
      provider.isVolumeAvailable = false
      assertFalse(scanner.scan { false })
      assertEquals(0L, dao.getScanState()?.generation)
      assertEquals(0, classifierCreations)

      provider.isVolumeAvailable = true
      Shadows.shadowOf(application).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)
      assertTrue(scanner.scan { false })
      assertEquals(0, classifierCreations)
    } finally {
      database.close()
    }
  }

  @Test
  fun classifierInitializationFailureKeepsPhotoForRetry() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.generation = 1
    provider.photos = listOf(Photo(1, 1, 1000))
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = provider.version, generation = 0, enrolledAt = 1))
      val scanner = PhotoScanner(application, dao) {
        throw IllegalStateException("test classifier initialization failure")
      }
      assertThrows(IllegalStateException::class.java) { scanner.scan { false } }
      assertTrue(dao.getResults().isEmpty())
      assertEquals(0L, dao.getScanState()?.generation)
    } finally {
      database.close()
    }
  }

  @Test
  fun onlyFoodPhotosAreSubmittedWithTheirOriginalUriAndCaptureTime() {
    val application = RuntimeEnvironment.getApplication() as Application
    Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    val provider = Robolectric.setupContentProvider(PhotoMediaProvider::class.java, "media")
    provider.generation = 2
    provider.photos = listOf(Photo(1, 1, 1000), Photo(2, 2, 2000))
    val database = Room.inMemoryDatabaseBuilder(application, MealRelayDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val dao = database.photoProcessingDao()
      dao.insertScanState(PhotoScanStateEntity(version = provider.version, generation = 0, enrolledAt = 1))
      val submissions = mutableListOf<Pair<Uri, Long>>()
      val scanner = PhotoScanner(
        application,
        dao,
        submitFoodPhoto = { uri, capturedAt -> submissions.add(uri to capturedAt) },
        createClassifier = { { uri -> ContentUris.parseId(uri) == 1L } },
      )

      assertTrue(scanner.scan { false })
      assertEquals(
        listOf(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 1L) to 1000L),
        submissions,
      )

      assertTrue(scanner.scan { false })
      assertEquals(1, submissions.size)
    } finally {
      database.close()
    }
  }

  data class Photo(
    val id: Long,
    val modifiedGeneration: Long,
    val capturedAt: Long,
    val addedGeneration: Long = modifiedGeneration,
  )

  class PhotoMediaProvider : ContentProvider() {
    var version = "version-1"
    var generation = 0L
    var photos = emptyList<Photo>()
    var isVolumeAvailable = true

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = Bundle().apply {
      when (method) {
        "get_version" -> putString(Intent.EXTRA_TEXT, if (isVolumeAvailable) version else null)
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
      val afterGeneration = requireNotNull(selectionArgs)[0].toLong()
      val scanGeneration = selectionArgs[1].toLong()
      val cursor = MatrixCursor(columns)
      for (photo in photos.sortedWith(compareBy(Photo::modifiedGeneration, Photo::id))) {
        if (photo.modifiedGeneration <= afterGeneration || photo.modifiedGeneration > scanGeneration) continue
        cursor.addRow(columns.map { column ->
          when (column) {
            MediaStore.Images.Media._ID -> photo.id
            MediaStore.Images.Media.GENERATION_ADDED -> photo.addedGeneration
            MediaStore.Images.Media.GENERATION_MODIFIED -> photo.modifiedGeneration
            MediaStore.Images.Media.DATE_TAKEN -> photo.capturedAt
            MediaStore.Images.Media.RELATIVE_PATH -> "DCIM/Camera/"
            MediaStore.Images.Media.OWNER_PACKAGE_NAME -> null
            else -> error("Unexpected column: $column")
          }
        })
      }
      return cursor
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
}
