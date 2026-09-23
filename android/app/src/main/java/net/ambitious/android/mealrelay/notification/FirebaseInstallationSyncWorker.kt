package net.ambitious.android.mealrelay.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.notification.network.FirebaseInstallationRegistrationException
import java.io.IOException
import kotlinx.coroutines.CancellationException

@HiltWorker
class FirebaseInstallationSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted parameters: WorkerParameters,
  private val tokenStore: MealRelayTokenStore,
  private val installationRepository: FirebaseInstallationRepository,
) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    val fid = inputData.getString(FID)?.takeIf(String::isNotBlank) ?: return Result.failure()
    val token = tokenStore.read() ?: return Result.success()
    return try {
      installationRepository.register(token, fid)
      Result.success()
    } catch (exception: FirebaseInstallationRegistrationException) {
      if (exception.isRetryable) Result.retry() else Result.failure()
    } catch (_: IOException) {
      Result.retry()
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: Exception) {
      Result.failure()
    }
  }

  companion object {
    const val FID = "firebase_installation_id"
  }
}
