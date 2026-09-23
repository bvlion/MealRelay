package net.ambitious.android.mealrelay.notification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ambitious.android.mealrelay.notification.network.FirebaseInstallationTransport
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseInstallationRepository @Inject constructor(
  private val transport: FirebaseInstallationTransport,
) {
  suspend fun register(token: String, fid: String) = withContext(Dispatchers.IO) {
    transport.register(token, fid)
  }
}
