package net.ambitious.android.mealrelay.notification

import android.content.Context
import com.google.firebase.installations.FirebaseInstallations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.notification.network.FirebaseInstallationTransport
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseInstallationRepository @Inject constructor(
  @ApplicationContext context: Context,
  private val tokenStore: MealRelayTokenStore,
  private val transport: FirebaseInstallationTransport,
) {
  private val preferences = context.getSharedPreferences("mealrelay_firebase_installation", Context.MODE_PRIVATE)

  suspend fun synchronize() = withContext(Dispatchers.IO) {
    val token = tokenStore.read() ?: return@withContext
    val fid = FirebaseInstallations.getInstance().id.await()
    val previousFid = preferences.getString(KEY_FID, null)
    if (previousFid == fid) return@withContext
    transport.register(token, fid, previousFid)
    check(preferences.edit().putString(KEY_FID, fid).commit())
  }

  private companion object {
    const val KEY_FID = "fid"
  }
}
