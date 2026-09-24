package net.ambitious.android.mealrelay.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import net.ambitious.android.mealrelay.MealRelayMainActivity
import net.ambitious.android.mealrelay.R
import java.util.UUID
import javax.inject.Inject

class MealRegistrationNotificationPresenter @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  fun show(messageId: String?, title: String?, body: String?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val notificationTag = messageId ?: UUID.randomUUID().toString()
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(NotificationChannel(
      CHANNEL_ID,
      context.getString(R.string.meal_recorded_channel),
      NotificationManager.IMPORTANCE_DEFAULT,
    ))
    val pendingIntent = PendingIntent.getActivity(
      context,
      notificationTag.hashCode(),
      Intent(context, MealRelayMainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .setData(Uri.parse("mealrelay://meal-recorded/$notificationTag")),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    notificationManager.notify(
      notificationTag,
      NOTIFICATION_ID,
      Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle(title ?: context.getString(R.string.firebase_meal_recorded_title))
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
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
