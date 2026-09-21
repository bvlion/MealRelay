package net.ambitious.android.mealrelay.submission

class MealSubmissionQueue(
  private val repository: MealSubmissionRepository,
  private val scheduler: MealSubmissionWorkScheduler,
  private val clock: () -> Long,
) {
  fun enqueue(draft: MealSubmissionDraft): String {
    val mealId = repository.enqueue(draft, clock())
    scheduler.schedule(mealId, clock())
    return mealId
  }
}
