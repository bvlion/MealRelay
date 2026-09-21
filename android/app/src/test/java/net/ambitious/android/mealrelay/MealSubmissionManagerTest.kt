package net.ambitious.android.mealrelay

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MealSubmissionManagerTest {
  private val database = Room.inMemoryDatabaseBuilder(
    RuntimeEnvironment.getApplication() as Application,
    PhotoProcessingDatabase::class.java,
  ).allowMainThreadQueries().build()
  private val dao = database.photoProcessingDao()

  @After
  fun closeDatabase() {
    database.close()
  }

  @Test
  fun automaticSubmissionRetriesAtMostThreeTimesAndRetainsTheFinalFailure() = runBlocking {
    val submittedMealIds = mutableListOf<String>()
    val submission = textSubmission("text-1")
    dao.insertMealSubmission(submission)
    val manager = MealSubmissionManager(dao) { queuedSubmission ->
      submittedMealIds.add(queuedSubmission.mealId)
      throw IOException("offline")
    }

    assertEquals(AutomaticSubmissionResult.RETRY, manager.submitAutomatically(submission))
    assertEquals(AutomaticSubmissionResult.RETRY, manager.submitAutomatically(submission))
    assertEquals(AutomaticSubmissionResult.FAILED, manager.submitAutomatically(submission))

    assertEquals(listOf("text-1", "text-1", "text-1"), submittedMealIds)
    assertEquals(3, dao.getMealSubmission("text-1")?.automaticAttemptCount)
    assertEquals(MealSubmissionEntity.STATE_FAILED, dao.getMealSubmission("text-1")?.state)
  }

  @Test
  fun automaticSubmissionRetriesOnlyNetworkAndSpecifiedHttpFailures() = runBlocking {
    val retryableFailures = listOf(IOException("network"), httpFailure(408), httpFailure(429), httpFailure(500))
    retryableFailures.forEachIndexed { index, error ->
      val submission = textSubmission("retryable-$index")
      dao.insertMealSubmission(submission)
      assertEquals(AutomaticSubmissionResult.RETRY, MealSubmissionManager(dao) { throw error }
        .submitAutomatically(submission))
    }
    listOf(400, 401, 404).forEach { status ->
      val submission = textSubmission("client-error-$status")
      dao.insertMealSubmission(submission)
      assertEquals(AutomaticSubmissionResult.FAILED, MealSubmissionManager(dao) { throw httpFailure(status) }
        .submitAutomatically(submission))
      assertEquals(MealSubmissionEntity.STATE_FAILED, dao.getMealSubmission("client-error-$status")?.state)
    }
  }

  @Test
  fun automaticRetryKeepsTheSameMealIdTextAndInputTime() = runBlocking {
    val submission = textSubmission("same-id")
    dao.insertMealSubmission(submission)
    val sentSubmissions = mutableListOf<MealSubmissionEntity>()
    val manager = MealSubmissionManager(dao) { queuedSubmission ->
      sentSubmissions.add(queuedSubmission)
      if (sentSubmissions.size == 1) throw IOException("response lost")
    }

    assertEquals(AutomaticSubmissionResult.RETRY, manager.submitAutomatically(submission))
    assertEquals(AutomaticSubmissionResult.SUCCEEDED, manager.submitAutomatically(submission))

    assertEquals(listOf("same-id", "same-id"), sentSubmissions.map { it.mealId })
    assertEquals(listOf("朝の食事", "朝の食事"), sentSubmissions.map { it.text })
    assertEquals(listOf(1_000L, 1_000L), sentSubmissions.map { it.occurredAt })
    assertNull(dao.getMealSubmission("same-id"))
  }

  @Test
  fun successfulAutomaticSubmissionDeletesTheQueueItem() = runBlocking {
    val submission = imageSubmission("image-1")
    dao.insertMealSubmission(submission)

    assertEquals(AutomaticSubmissionResult.SUCCEEDED,
      MealSubmissionManager(dao) {}.submitAutomatically(submission))
    assertNull(dao.getMealSubmission("image-1"))
  }

  @Test
  fun manualSubmissionHasNoAttemptLimitAndKeepsFailuresForLaterRetries() = runBlocking {
    val submission = textSubmission("manual-1").copy(state = MealSubmissionEntity.STATE_FAILED)
    dao.insertMealSubmission(submission)
    var calls = 0
    val manager = MealSubmissionManager(dao) {
      calls++
      if (calls <= 5) throw IOException("offline")
    }

    repeat(5) { assertEquals(ManualSubmissionResult.FAILED, manager.submitManually("manual-1")) }
    assertEquals(MealSubmissionEntity.STATE_FAILED, dao.getMealSubmission("manual-1")?.state)
    assertEquals(0, dao.getMealSubmission("manual-1")?.automaticAttemptCount)
    assertEquals(ManualSubmissionResult.SUCCEEDED, manager.submitManually("manual-1"))
    assertEquals(6, calls)
    assertNull(dao.getMealSubmission("manual-1"))
  }

  private fun textSubmission(mealId: String) = MealSubmissionEntity(
    mealId = mealId,
    type = MealSubmissionEntity.TYPE_TEXT,
    imageUri = null,
    text = "朝の食事",
    occurredAt = 1_000L,
    createdAt = 2_000L,
  )

  private fun imageSubmission(mealId: String) = MealSubmissionEntity(
    mealId = mealId,
    type = MealSubmissionEntity.TYPE_IMAGE,
    imageUri = "content://media/external/images/media/1",
    text = null,
    occurredAt = 1_000L,
    createdAt = 2_000L,
  )

  private fun httpFailure(status: Int): HttpException = HttpException(Response.error<Unit>(
    status,
    "error".toResponseBody("text/plain".toMediaType()),
  ))
}
