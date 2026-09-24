package net.ambitious.android.mealrelay.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class FirebaseInstallationSyncScheduler @Inject constructor(
  @ApplicationContext context: Context,
) {
  private val workManager = WorkManager.getInstance(context)

  fun schedule(fid: String) {
    val request = OneTimeWorkRequestBuilder<FirebaseInstallationSyncWorker>()
      .setInputData(workDataOf(FirebaseInstallationSyncWorker.FID to fid))
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
  }

  private companion object {
    const val WORK_NAME = "firebase_installation_registration"
  }
}
