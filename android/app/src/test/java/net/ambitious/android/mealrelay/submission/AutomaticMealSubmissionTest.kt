package net.ambitious.android.mealrelay.submission

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.ambitious.android.mealrelay.data.MealRelayDatabase
import net.ambitious.android.mealrelay.data.MealSubmissionEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AutomaticMealSubmissionTest {
  private val database = Room.inMemoryDatabaseBuilder(
    RuntimeEnvironment.getApplication() as Application,
    MealRelayDatabase::class.java,
  ).allowMainThreadQueries().build()
  private val repository = MealSubmissionRepository(database.mealSubmissionDao())
  private var now = 1_000L

  @After
  fun closeDatabase() {
    database.close()
  }

  @Test
  fun retryableFailuresUsePerMealThreeAttemptLimitAndBackoff() = runBlocking {
    insert(textSubmission("meal-1"))
    insert(textSubmission("meal-2"))
    val sender = FakeSender(MealSubmissionSendResult.RetryableFailure(IllegalStateException()))
    val submission = AutomaticMealSubmission(repository, sender) { now }

    assertEquals(
      AutomaticMealSubmissionResult.RetryAt(now + AutomaticMealSubmission.FIRST_RETRY_DELAY_MILLIS),
      submission.submit("meal-1"),
    )
    assertEquals(1, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(0, repository.get("meal-2")?.automaticAttemptCount)

    now += AutomaticMealSubmission.FIRST_RETRY_DELAY_MILLIS
    assertEquals(
      AutomaticMealSubmissionResult.RetryAt(now + AutomaticMealSubmission.SECOND_RETRY_DELAY_MILLIS),
      submission.submit("meal-1"),
    )
    now += AutomaticMealSubmission.SECOND_RETRY_DELAY_MILLIS
    assertEquals(AutomaticMealSubmissionResult.Failed, submission.submit("meal-1"))
    assertEquals(MealSubmissionEntity.STATE_FAILED, repository.get("meal-1")?.state)
    assertEquals(3, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(0, repository.get("meal-2")?.automaticAttemptCount)
  }

  @Test
  fun authenticationWaitDoesNotFailOrConsumeAnAutomaticAttempt() = runBlocking {
    insert(textSubmission("meal-1"))
    val sender = FakeSender(MealSubmissionSendResult.AuthenticationRequired)
    val submission = AutomaticMealSubmission(repository, sender) { now }

    assertEquals(AutomaticMealSubmissionResult.Deferred, submission.submit("meal-1"))
    assertEquals(MealSubmissionEntity.STATE_PENDING, repository.get("meal-1")?.state)
    assertEquals(0, repository.get("meal-1")?.automaticAttemptCount)

    sender.result = MealSubmissionSendResult.Succeeded
    assertEquals(AutomaticMealSubmissionResult.Completed, submission.submit("meal-1"))
    assertNull(repository.get("meal-1"))
  }

  @Test
  fun permanentFailureEndsAutomaticSubmissionAfterItsFirstAttempt() = runBlocking {
    insert(textSubmission("meal-1"))
    val submission = AutomaticMealSubmission(
      repository,
      FakeSender(MealSubmissionSendResult.PermanentFailure(IllegalArgumentException())),
      { now },
    )

    assertEquals(AutomaticMealSubmissionResult.Failed, submission.submit("meal-1"))
    assertEquals(MealSubmissionEntity.STATE_FAILED, repository.get("meal-1")?.state)
    assertEquals(1, repository.get("meal-1")?.automaticAttemptCount)
  }

  @Test
  fun onlySpecifiedHttpStatusesAreRetryable() {
    assertEquals(true, isRetryableBackendStatus(408))
    assertEquals(true, isRetryableBackendStatus(429))
    assertEquals(true, isRetryableBackendStatus(500))
    assertEquals(false, isRetryableBackendStatus(400))
    assertEquals(false, isRetryableBackendStatus(401))
    assertEquals(false, isRetryableBackendStatus(404))
  }

  @Test
  fun onlyNetworkIoExceptionsAreRetryable() {
    assertEquals(true, isRetryableNetworkException(SocketTimeoutException()))
    assertEquals(false, isRetryableNetworkException(FileNotFoundException()))
  }

  @Test
  fun cancellationDoesNotPersistAnUncompletedAutomaticAttempt() = runBlocking {
    insert(textSubmission("meal-1"))
    val submission = AutomaticMealSubmission(repository, MealSubmissionSender { throw CancellationException() }) { now }

    assertThrows(CancellationException::class.java) { runBlocking { submission.submit("meal-1") } }
    assertEquals(0, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(MealSubmissionEntity.STATE_PENDING, repository.get("meal-1")?.state)
  }

  @Test
  fun manualFailuresRemainRetryableWithoutChangingAutomaticCount() = runBlocking {
    insert(textSubmission("meal-1").copy(state = MealSubmissionEntity.STATE_FAILED, automaticAttemptCount = 3))
    val sender = FakeSender(MealSubmissionSendResult.PermanentFailure(IllegalStateException()))
    val submission = AutomaticMealSubmission(repository, sender) { now }

    repeat(5) { assertEquals(ManualMealSubmissionResult.Failed, submission.submitManually("meal-1")) }
    assertEquals(3, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(MealSubmissionEntity.STATE_FAILED, repository.get("meal-1")?.state)

    sender.result = MealSubmissionSendResult.Succeeded
    assertEquals(ManualMealSubmissionResult.Succeeded, submission.submitManually("meal-1"))
    assertNull(repository.get("meal-1"))
  }

  @Test
  fun senderReceivesTheOriginalMealIdAndTimeStringOnRetry() = runBlocking {
    val originalTime = "2026-09-22T08:00:00+09:00"
    insert(textSubmission("meal-1").copy(occurredAt = originalTime))
    val sender = FakeSender(MealSubmissionSendResult.RetryableFailure(IllegalStateException()))
    val submission = AutomaticMealSubmission(repository, sender) { now }

    submission.submit("meal-1")
    now += AutomaticMealSubmission.FIRST_RETRY_DELAY_MILLIS
    submission.submit("meal-1")

    assertEquals(listOf("meal-1", "meal-1"), sender.submissions.map { it.mealId })
    assertEquals(listOf(originalTime, originalTime), sender.submissions.map { it.occurredAt })
  }

  @Test
  fun queueCreatesUuidMealIdAndPreservesTextAndInputTime() {
    val mealId = repository.enqueue(
      MealSubmissionDraft(
        type = MealSubmissionEntity.TYPE_TEXT,
        imageUri = null,
        text = "朝の食事",
        occurredAt = "2026-09-22T08:00:00+09:00",
      ),
      now,
    )

    assertEquals(mealId, UUID.fromString(mealId).toString())
    assertEquals("朝の食事", repository.get(mealId)?.text)
    assertEquals("2026-09-22T08:00:00+09:00", repository.get(mealId)?.occurredAt)
  }

  private fun insert(submission: MealSubmissionEntity) {
    database.mealSubmissionDao().insert(submission)
  }

  private fun textSubmission(mealId: String) = MealSubmissionEntity(
    mealId = mealId,
    type = MealSubmissionEntity.TYPE_TEXT,
    imageUri = null,
    text = "朝の食事",
    occurredAt = "2026-09-22T08:00:00+09:00",
    createdAt = now,
  )

  private class FakeSender(initialResult: MealSubmissionSendResult) : MealSubmissionSender {
    var result = initialResult
    val submissions = mutableListOf<MealSubmissionEntity>()

    override suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult {
      submissions.add(submission)
      return result
    }
  }
}
