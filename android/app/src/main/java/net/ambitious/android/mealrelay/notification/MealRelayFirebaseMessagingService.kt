package net.ambitious.android.mealrelay.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import net.ambitious.android.mealrelay.MealRelayMainActivity
import net.ambitious.android.mealrelay.R
import javax.inject.Inject

@AndroidEntryPoint
class MealRelayFirebaseMessagingService : FirebaseMessagingService() {
  @Inject lateinit var installationSyncScheduler: FirebaseInstallationSyncScheduler

  override fun onRegistered(installationId: String) {
    installationSyncScheduler.schedule(installationId)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    val notification = message.notification ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(NotificationChannel(
      CHANNEL_ID,
      getString(R.string.meal_recorded_channel),
      NotificationManager.IMPORTANCE_DEFAULT,
    ))
    val pendingIntent = PendingIntent.getActivity(
      this,
      NOTIFICATION_ID,
      Intent(this, MealRelayMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    notificationManager.notify(
      NOTIFICATION_ID,
      Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle(notification.title ?: getString(R.string.firebase_meal_recorded_title))
        .setContentText(notification.body)
        .setStyle(Notification.BigTextStyle().bigText(notification.body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build(),
    )
  }

  private companion object {
    const val CHANNEL_ID = "meal_recorded"
    const val NOTIFICATION_ID = 15
  }
}
