package net.ambitious.android.mealrelay.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MealRelayFirebaseMessagingService : FirebaseMessagingService() {
  @Inject lateinit var installationSyncScheduler: FirebaseInstallationSyncScheduler
  @Inject lateinit var mealRegistrationNotificationPresenter: MealRegistrationNotificationPresenter

  override fun onRegistered(installationId: String) {
    installationSyncScheduler.schedule(installationId)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    val notification = message.notification ?: return
    mealRegistrationNotificationPresenter.show(
      message.messageId,
      notification.title,
      notification.body,
    )
  }
}
