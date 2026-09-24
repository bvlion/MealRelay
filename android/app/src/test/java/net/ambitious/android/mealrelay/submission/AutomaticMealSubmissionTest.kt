package net.ambitious.android.mealrelay.submission

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.ambitious.android.mealrelay.data.database.MealRelayDatabase
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.ui.FailedMealSubmission
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionType
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionFailureClassifier
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionReadinessChecker
import net.ambitious.android.mealrelay.submission.network.ImageMealSubmissionFailureClassifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.UUID
import java.time.Instant
import java.util.TimeZone

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
    val submission = AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now })

    assertEquals(
      AutomaticMealSubmissionResult.RetryAt(now + AutomaticMealSubmissionProcessor.FIRST_RETRY_DELAY_MILLIS),
      submission.submit("meal-1"),
    )
    assertEquals(1, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(0, repository.get("meal-2")?.automaticAttemptCount)

    now += AutomaticMealSubmissionProcessor.FIRST_RETRY_DELAY_MILLIS
    assertEquals(
      AutomaticMealSubmissionResult.RetryAt(now + AutomaticMealSubmissionProcessor.SECOND_RETRY_DELAY_MILLIS),
      submission.submit("meal-1"),
    )
    now += AutomaticMealSubmissionProcessor.SECOND_RETRY_DELAY_MILLIS
    assertEquals(AutomaticMealSubmissionResult.Failed(true), submission.submit("meal-1"))
    assertEquals(MealSubmissionEntity.STATE_FAILED, repository.get("meal-1")?.state)
    assertEquals(3, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(0, repository.get("meal-2")?.automaticAttemptCount)
  }

  @Test
  fun authenticationWaitDoesNotFailOrConsumeAnAutomaticAttempt() = runBlocking {
    insert(textSubmission("meal-1"))
    val sender = FakeSender(
      MealSubmissionSendResult.Succeeded,
      MealSubmissionReadiness.AwaitingAuthorization,
    )
    val submission = AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now })

    assertEquals(AutomaticMealSubmissionResult.AwaitingAuthorization, submission.submit("meal-1"))
    assertEquals(MealSubmissionEntity.STATE_PENDING, repository.get("meal-1")?.state)
    assertEquals(0, repository.get("meal-1")?.automaticAttemptCount)

    sender.readiness = MealSubmissionReadiness.Ready
    assertEquals(AutomaticMealSubmissionResult.Completed, submission.submit("meal-1"))
    assertNull(repository.get("meal-1"))
  }

  @Test
  fun permanentFailureEndsAutomaticSubmissionAfterItsFirstAttempt() = runBlocking {
    insert(textSubmission("meal-1"))
    val submission = AutomaticMealSubmissionProcessor(
      repository,
      FakeSender(MealSubmissionSendResult.PermanentFailure(IllegalArgumentException())),
      SubmissionClock { now },
    )

    assertEquals(AutomaticMealSubmissionResult.Failed(true), submission.submit("meal-1"))
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
  fun textTransportIoExceptionsAreRetryable() {
    val result = TextMealSubmissionFailureClassifier().classify(IOException())

    assertEquals(true, result is MealSubmissionSendResult.RetryableFailure)
  }

  @Test
  fun rejectedImageSubmissionCannotBeManuallyRetried() = runBlocking {
    insert(textSubmission("meal-1"))
    val rejection = ImageMealSubmissionFailureClassifier().classify(
      retrofit2.HttpException(retrofit2.Response.error<Any>(415, "".toResponseBody())),
    )
    val automaticSubmission = AutomaticMealSubmissionProcessor(
      repository,
      FakeSender(rejection),
      SubmissionClock { now },
    )

    assertEquals(AutomaticMealSubmissionResult.Failed(false), automaticSubmission.submit("meal-1"))
    assertEquals(false, repository.get("meal-1")?.isManualRetryAvailable)
    assertEquals(
      ManualMealSubmissionResult.NotRetryable,
      ManualMealSubmission(repository, FakeSender()).submit("meal-1"),
    )
  }

  @Test
  fun textSubmissionIsUnavailableUntilTheTextEndpointIsConfigured() {
    val tokenStore = MealRelayTokenStore(RuntimeEnvironment.getApplication())
    tokenStore.write("test-device-token")
    val checker = TextMealSubmissionReadinessChecker(
      tokenStore,
      "",
    )

    assertEquals(MealSubmissionReadiness.Unavailable, checker.check(textSubmission("meal-1")))
  }

  @Test
  fun cancellationPersistsAnAutomaticAttemptThatMayHaveStartedNetworkCommunication() = runBlocking {
    insert(textSubmission("meal-1"))
    val submission = AutomaticMealSubmissionProcessor(
      repository,
      FakeSender(sendBlock = { throw CancellationException() }),
      SubmissionClock { now },
    )

    assertThrows(CancellationException::class.java) { runBlocking { submission.submit("meal-1") } }
    assertEquals(1, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(MealSubmissionEntity.STATE_SENDING, repository.get("meal-1")?.state)
    assertEquals(
      now + AutomaticMealSubmissionProcessor.FIRST_RETRY_DELAY_MILLIS,
      repository.get("meal-1")?.nextAutomaticAttemptAt,
    )
  }

  @Test
  fun interruptedAttemptCountsTowardTheThreeAutomaticSendLimit() = runBlocking {
    insert(textSubmission("meal-1"))
    val interrupted = AutomaticMealSubmissionProcessor(
      repository,
      FakeSender(sendBlock = { throw CancellationException() }),
      SubmissionClock { now },
    )
    assertThrows(CancellationException::class.java) { runBlocking { interrupted.submit("meal-1") } }

    val sender = FakeSender(MealSubmissionSendResult.RetryableFailure(IllegalStateException()))
    val resumed = AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now })
    assertEquals(
      AutomaticMealSubmissionResult.RetryAt(now + AutomaticMealSubmissionProcessor.FIRST_RETRY_DELAY_MILLIS),
      resumed.submit("meal-1"),
    )
    now += AutomaticMealSubmissionProcessor.FIRST_RETRY_DELAY_MILLIS
    assertEquals(
      AutomaticMealSubmissionResult.RetryAt(now + AutomaticMealSubmissionProcessor.SECOND_RETRY_DELAY_MILLIS),
      resumed.submit("meal-1"),
    )
    now += AutomaticMealSubmissionProcessor.SECOND_RETRY_DELAY_MILLIS
    assertEquals(AutomaticMealSubmissionResult.Failed(true), resumed.submit("meal-1"))
    assertEquals(2, sender.submissions.size)
    assertEquals(3, repository.get("meal-1")?.automaticAttemptCount)
  }

  @Test
  fun unavailableSenderFailsWithoutStartingAnAutomaticAttempt() = runBlocking {
    insert(textSubmission("meal-1"))
    val sender = FakeSender(
      MealSubmissionSendResult.Succeeded,
      MealSubmissionReadiness.Unavailable,
    )

    assertEquals(
      AutomaticMealSubmissionResult.Failed(true),
      AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now }).submit("meal-1"),
    )
    assertEquals(0, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(MealSubmissionEntity.STATE_FAILED, repository.get("meal-1")?.state)
    assertEquals(emptyList<MealSubmissionEntity>(), sender.submissions)
  }

  @Test
  fun interruptedThirdAttemptEndsAsFailedWithoutStartingAFourthSend() = runBlocking {
    insert(textSubmission("meal-1").copy(
      automaticAttemptCount = 3,
      state = MealSubmissionEntity.STATE_SENDING,
    ))
    val sender = FakeSender()

    assertEquals(
      AutomaticMealSubmissionResult.Failed(true),
      AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now }).submit("meal-1"),
    )
    assertEquals(MealSubmissionEntity.STATE_FAILED, repository.get("meal-1")?.state)
    assertEquals(emptyList<MealSubmissionEntity>(), sender.submissions)
  }

  @Test
  fun manualFailuresRemainRetryableWithoutChangingAutomaticCount() = runBlocking {
    insert(textSubmission("meal-1").copy(state = MealSubmissionEntity.STATE_FAILED, automaticAttemptCount = 3))
    val sender = FakeSender(MealSubmissionSendResult.PermanentFailure(IllegalStateException()))
    val submission = ManualMealSubmission(repository, sender)

    repeat(5) { assertEquals(ManualMealSubmissionResult.Failed, submission.submit("meal-1")) }
    assertEquals(3, repository.get("meal-1")?.automaticAttemptCount)
    assertEquals(MealSubmissionEntity.STATE_FAILED, repository.get("meal-1")?.state)

    sender.result = MealSubmissionSendResult.Succeeded
    assertEquals(ManualMealSubmissionResult.Succeeded, submission.submit("meal-1"))
    assertNull(repository.get("meal-1"))
  }

  @Test
  fun senderReceivesTheOriginalMealIdAndTimeStringOnRetry() = runBlocking {
    val originalTime = "2026-09-22T08:00:00+09:00"
    insert(textSubmission("meal-1").copy(occurredAt = originalTime))
    val sender = FakeSender(MealSubmissionSendResult.RetryableFailure(IllegalStateException()))
    val submission = AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now })

    submission.submit("meal-1")
    now += AutomaticMealSubmissionProcessor.FIRST_RETRY_DELAY_MILLIS
    submission.submit("meal-1")

    assertEquals(listOf("meal-1", "meal-1"), sender.submissions.map { it.mealId })
    assertEquals(listOf(originalTime, originalTime), sender.submissions.map { it.occurredAt })
  }

  @Test
  fun queueCreatesUuidMealIdAndPreservesTextAndInputTime() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val mealId = queue.enqueue(
      MealSubmissionDraft(
        type = MealSubmissionEntity.TYPE_TEXT,
        imageUri = null,
        text = "朝の食事",
        occurredAt = "2026-09-22T08:00:00+09:00",
      ),
    )

    assertEquals(mealId, UUID.fromString(mealId).toString())
    assertEquals("朝の食事", repository.get(mealId)?.text)
    assertEquals("2026-09-22T08:00:00+09:00", repository.get(mealId)?.occurredAt)
  }

  @Test
  fun foodPhotoQueueAndProcessedResultAreAtomicAcrossMediaStoreVersions() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val photoUri = Uri.parse("content://media/external/images/media/1")
    val occurredAt = "2026-09-22T08:00:00.000+09:00"

    val mealId = queue.enqueueFoodPhoto(photoUri, 1_000L, "version-1", occurredAt)
    assertEquals(true, mealId != null)
    repository.delete(requireNotNull(mealId))

    assertNull(queue.enqueueFoodPhoto(photoUri, 1_000L, "version-2", occurredAt))
    assertEquals(emptyList<MealSubmissionEntity>(), repository.pendingSubmissions())
    assertEquals(1, database.photoProcessingDao().getResults().size)
  }

  @Test
  fun foodPhotosWithinFifteenMinutesShareOneQueuedMealAndTheFirstCaptureTime() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val firstCaptureAt = System.currentTimeMillis()
    val firstMealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/11"),
      firstCaptureAt,
      "version-1",
      "2026-09-22T08:00:00.000+09:00",
    )
    val secondMealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/12"),
      firstCaptureAt + 14 * 60 * 1000L,
      "version-1",
      "2026-09-22T08:14:00.000+09:00",
    )

    assertEquals(firstMealId, secondMealId)
    assertEquals(1, repository.pendingSubmissions().size)
    assertEquals("2026-09-22T08:00:00.000+09:00", repository.get(requireNotNull(firstMealId))?.occurredAt)
    assertEquals(
      2,
      ImageMealSubmissionPayload.decode(
        requireNotNull(repository.get(requireNotNull(firstMealId))?.imagePayload),
      ).photos.size,
    )
  }

  @Test
  fun foodPhotoAtFifteenMinuteBoundaryStartsAnotherMeal() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val firstCaptureAt = System.currentTimeMillis()
    val firstMealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/21"),
      firstCaptureAt,
      "version-1",
      "2026-09-22T08:00:00.000+09:00",
    )
    val secondMealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/22"),
      firstCaptureAt + MealSubmissionQueue.FOOD_MEAL_WINDOW_MILLIS,
      "version-1",
      "2026-09-22T08:15:00.000+09:00",
    )

    assertEquals(false, firstMealId == secondMealId)
    assertEquals(2, repository.pendingSubmissions().size)
  }

  @Test
  fun foodPhotosAreGroupedByCaptureTimeWhenScannerReportsThemOutOfOrder() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val earlierCaptureAt = System.currentTimeMillis()
    val laterMealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/31"),
      earlierCaptureAt + 10 * 60 * 1000L,
      "version-1",
      "2026-09-22T08:10:00.000+09:00",
    )
    val earlierMealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/32"),
      earlierCaptureAt,
      "version-1",
      "2026-09-22T08:00:00.000+09:00",
    )

    assertEquals(laterMealId, earlierMealId)
    assertEquals(1, repository.pendingSubmissions().size)
    val submission = repository.get(requireNotNull(earlierMealId))
    assertEquals("2026-09-22T08:00:00.000+09:00", submission?.occurredAt)
    assertEquals(
      listOf(earlierCaptureAt, earlierCaptureAt + 10 * 60 * 1000L),
      ImageMealSubmissionPayload.decode(requireNotNull(submission?.imagePayload)).photos.map { it.capturedAt },
    )
  }

  @Test
  fun attemptedPendingImagePayloadIsNotChangedByANewFoodPhoto() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val firstCaptureAt = System.currentTimeMillis()
    val firstMealId = requireNotNull(queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/41"),
      firstCaptureAt,
      "version-1",
      "2026-09-22T08:00:00.000+09:00",
    ))
    val originalPayload = repository.get(firstMealId)?.imagePayload
    repository.beginAutomaticAttempt(firstMealId, 3, firstCaptureAt + 20 * 60 * 1000L)
    repository.recordAutomaticRetry(firstMealId, 1, firstCaptureAt + 25 * 60 * 1000L)

    val nextMealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/42"),
      firstCaptureAt + 5 * 60 * 1000L,
      "version-1",
      "2026-09-22T08:05:00.000+09:00",
    )

    assertEquals(false, firstMealId == nextMealId)
    assertEquals(originalPayload, repository.get(firstMealId)?.imagePayload)
    assertEquals(1, ImageMealSubmissionPayload.decode(requireNotNull(repository.get(firstMealId)?.imagePayload)).photos.size)
    assertEquals(1, ImageMealSubmissionPayload.decode(requireNotNull(repository.get(requireNotNull(nextMealId))?.imagePayload)).photos.size)
  }

  @Test
  fun legacyImageUriWithoutCaptureTimeIsNotGroupedWithANewFoodPhoto() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val captureAt = System.currentTimeMillis()
    val legacySubmission = MealSubmissionEntity(
      mealId = "legacy-image-meal",
      type = MealSubmissionEntity.TYPE_IMAGE,
      imagePayload = "content://media/external/images/media/51",
      text = null,
      occurredAt = "2026-09-22T08:00:00.000+09:00",
      createdAt = now,
    )
    insert(legacySubmission)

    val mealId = queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/52"),
      captureAt,
      "version-1",
      "2026-09-22T08:01:00.000+09:00",
    )

    assertEquals(false, legacySubmission.mealId == mealId)
    assertEquals(legacySubmission.imagePayload, repository.get(legacySubmission.mealId)?.imagePayload)
    assertEquals(2, repository.pendingSubmissions().size)
  }

  @Test
  fun foodPhotoDeadlineSurvivesStartupRecoveryAndEarlyWorkerInvocation() = runBlocking {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val captureAt = now + 60_000L
    val mealId = requireNotNull(queue.enqueueFoodPhoto(
      Uri.parse("content://media/external/images/media/61"),
      captureAt,
      "version-1",
      "2026-09-22T08:00:00.000+09:00",
    ))
    val expectedDeadline = captureAt + MealSubmissionQueue.FOOD_MEAL_WINDOW_MILLIS
    val sender = FakeSender()
    val processor = AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now })
    val scheduler = MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository)

    scheduler.resumePendingSubmissions()

    assertEquals(expectedDeadline, repository.get(mealId)?.nextAutomaticAttemptAt)
    assertEquals(AutomaticMealSubmissionResult.RetryAt(expectedDeadline), processor.submit(mealId))
    assertEquals(emptyList<MealSubmissionEntity>(), sender.submissions)
  }

  @Test
  fun startupRecoveryRestoresAnOlderFoodPhotoDeadlineBeforeScheduling() = runBlocking {
    val captureAt = System.currentTimeMillis()
    val mealId = "meal-awaiting-food-group"
    insert(MealSubmissionEntity(
      mealId = mealId,
      type = MealSubmissionEntity.TYPE_IMAGE,
      imagePayload = ImageMealSubmissionPayload(listOf(
        ImageMealSubmissionPayload.Photo("content://media/external/images/media/71", captureAt),
      )).encode(),
      text = null,
      occurredAt = "2026-09-22T08:00:00.000+09:00",
      createdAt = now,
    ))
    val scheduler = MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository)
    scheduler.schedule(mealId, System.currentTimeMillis(), ExistingWorkPolicy.REPLACE)
    val expectedDeadline = captureAt + MealSubmissionQueue.FOOD_MEAL_WINDOW_MILLIS

    scheduler.resumePendingSubmissions()

    assertEquals(expectedDeadline, repository.get(mealId)?.nextAutomaticAttemptAt)
    assertEquals(
      1,
      WorkManager.getInstance(RuntimeEnvironment.getApplication())
        .getWorkInfosForUniqueWork(MealSubmissionWorkScheduler.workName(mealId))
        .get()
        .size,
    )
    val sender = FakeSender()
    assertEquals(
      AutomaticMealSubmissionResult.RetryAt(expectedDeadline),
      AutomaticMealSubmissionProcessor(repository, sender, SubmissionClock { now }).submit(mealId),
    )
    assertEquals(emptyList<MealSubmissionEntity>(), sender.submissions)
  }

  @Test
  fun photoSubmissionUsesTheLocalOffsetForItsCaptureInstant() {
    val queue = MealSubmissionQueue(
      repository,
      MealSubmissionWorkScheduler(RuntimeEnvironment.getApplication(), repository),
      database,
      FoodPhotoMealGrouping(),
    )
    val originalTimezone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
      PhotoMealSubmission(queue).enqueue(
        Uri.parse("content://media/external/images/media/2"),
        Instant.parse("2026-09-21T23:00:00Z").toEpochMilli(),
        "version-1",
      )

      assertEquals("2026-09-22T08:00:00.000+09:00", repository.pendingSubmissions().single().occurredAt)
    } finally {
      TimeZone.setDefault(originalTimezone)
    }
  }

  @Test
  fun startupRecoverySchedulesAPersistedPendingSubmission() = runBlocking {
    insert(textSubmission("meal-1"))
    val application = RuntimeEnvironment.getApplication()
    val scheduler = MealSubmissionWorkScheduler(application, repository)

    scheduler.resumePendingSubmissions()

    assertEquals(
      1,
      WorkManager.getInstance(application)
        .getWorkInfosForUniqueWork(MealSubmissionWorkScheduler.workName("meal-1"))
        .get()
        .size,
    )
  }

  @Test
  fun startupRecoveryKeepsExistingWorkForAQueuedMeal() = runBlocking {
    val mealId = "meal-existing"
    insert(textSubmission(mealId))
    val application = RuntimeEnvironment.getApplication()
    val scheduler = MealSubmissionWorkScheduler(application, repository)
    scheduler.schedule(mealId, System.currentTimeMillis(), ExistingWorkPolicy.KEEP)
    val existingWork = WorkManager.getInstance(application)
      .getWorkInfosForUniqueWork(MealSubmissionWorkScheduler.workName(mealId))
      .get()
      .single()

    scheduler.resumePendingSubmissions()

    assertEquals(
      existingWork.id,
      WorkManager.getInstance(application)
        .getWorkInfosForUniqueWork(MealSubmissionWorkScheduler.workName(mealId))
        .get()
        .single()
        .id,
    )
  }

  @Test
  fun failedSubmissionPresentationKeepsTheInformationNeededToChooseATextRetry() {
    val item = FailedMealSubmission.from(textSubmission("meal-1"))

    assertEquals("meal-1", item.mealId)
    assertEquals(FailedMealSubmissionType.Text, item.type)
    assertEquals("朝の食事", item.content)
    assertEquals("2026-09-22T08:00:00+09:00", item.occurredAt)
  }

  private fun insert(submission: MealSubmissionEntity) {
    database.mealSubmissionDao().insert(submission)
  }

  private fun textSubmission(mealId: String) = MealSubmissionEntity(
    mealId = mealId,
    type = MealSubmissionEntity.TYPE_TEXT,
    imagePayload = null,
    text = "朝の食事",
    occurredAt = "2026-09-22T08:00:00+09:00",
    createdAt = now,
  )

  private class FakeSender(
    initialResult: MealSubmissionSendResult = MealSubmissionSendResult.Succeeded,
    initialReadiness: MealSubmissionReadiness = MealSubmissionReadiness.Ready,
    private val sendBlock: (suspend (MealSubmissionEntity) -> MealSubmissionSendResult)? = null,
  ) : MealSubmissionSender {
    var result = initialResult
    var readiness = initialReadiness
    val submissions = mutableListOf<MealSubmissionEntity>()

    override suspend fun readiness(submission: MealSubmissionEntity): MealSubmissionReadiness = readiness

    override suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult {
      submissions.add(submission)
      return sendBlock?.invoke(submission) ?: result
    }
  }
}
