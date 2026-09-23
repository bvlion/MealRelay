package net.ambitious.android.mealrelay.submission

import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity

class MealSubmissionSenderRouter(
  private val textSender: MealSubmissionSender,
  private val imageSender: MealSubmissionSender,
) : MealSubmissionSender {
  override suspend fun readiness(submission: MealSubmissionEntity): MealSubmissionReadiness =
    senderFor(submission).readiness(submission)

  override suspend fun send(submission: MealSubmissionEntity): MealSubmissionSendResult =
    senderFor(submission).send(submission)

  private fun senderFor(submission: MealSubmissionEntity): MealSubmissionSender = when (submission.type) {
    MealSubmissionEntity.TYPE_TEXT -> textSender
    MealSubmissionEntity.TYPE_IMAGE -> imageSender
    else -> textSender
  }
}
