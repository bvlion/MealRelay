package net.ambitious.android.mealrelay.photo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import net.ambitious.android.mealrelay.PhotoEnrollmentWorker
import javax.inject.Inject

class AndroidPhotoEnrollmentScheduler @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : PhotoEnrollmentScheduler {
  override fun enqueue() {
    PhotoEnrollmentWorker.enqueue(context)
  }
}
