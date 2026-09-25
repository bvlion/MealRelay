package net.ambitious.android.mealrelay.ui.main

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.data.database.MealRelayDatabase
import net.ambitious.android.mealrelay.submission.FoodPhotoMealGrouping
import net.ambitious.android.mealrelay.submission.MealSubmissionQueue
import net.ambitious.android.mealrelay.submission.MealSubmissionRepository
import net.ambitious.android.mealrelay.submission.MealSubmissionWorkScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MealRelayMainViewModelTest {
  private val application = RuntimeEnvironment.getApplication() as Application
  private val database = Room.inMemoryDatabaseBuilder(
    application,
    MealRelayDatabase::class.java,
  ).allowMainThreadQueries().build()
  private val repository = MealSubmissionRepository(database.mealSubmissionDao())

  @After
  fun closeDatabase() {
    database.close()
  }

  @Test
  fun manualSubmissionUsesTheDeviceOffsetInInputTime() = runBlocking {
    val originalTimezone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
      val scheduler = MealSubmissionWorkScheduler(application, repository)
      val viewModel = MealRelayMainViewModel(
        MealRelayTokenStore(application),
        MealSubmissionQueue(repository, scheduler, database, FoodPhotoMealGrouping()),
        scheduler,
        Clock.fixed(Instant.parse("2026-09-21T23:30:00Z"), ZoneId.of("UTC")),
      )

      viewModel.submitManualMeal("昨日の夜 カレー").join()

      val submission = repository.pendingSubmissions().singleOrNull()
      assertNotNull(submission)
      assertEquals("昨日の夜 カレー", submission?.text)
      assertEquals("2026-09-22T08:30:00+09:00", submission?.occurredAt)
      assertEquals(MainAuthorizationState.Required, viewModel.authorizationState.value)
    } finally {
      TimeZone.setDefault(originalTimezone)
    }
  }

  @Test
  fun manualSubmissionUsesTheCurrentDeviceTimezoneForEachEntry() = runBlocking {
    val scheduler = MealSubmissionWorkScheduler(application, repository)
    val viewModel = MealRelayMainViewModel(
      MealRelayTokenStore(application),
      MealSubmissionQueue(repository, scheduler, database, FoodPhotoMealGrouping()),
      scheduler,
      Clock.fixed(Instant.parse("2026-09-22T00:30:00Z"), ZoneId.of("UTC")),
    )
    val originalTimezone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
      viewModel.submitManualMeal("東京").join()
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
      viewModel.submitManualMeal("ロサンゼルス").join()
    } finally {
      TimeZone.setDefault(originalTimezone)
    }

    val inputTimes = repository.pendingSubmissions().mapNotNull { it.occurredAt }.toSet()
    assertTrue(inputTimes.contains("2026-09-22T09:30:00+09:00"))
    assertTrue(inputTimes.contains("2026-09-21T17:30:00-07:00"))
  }
}
