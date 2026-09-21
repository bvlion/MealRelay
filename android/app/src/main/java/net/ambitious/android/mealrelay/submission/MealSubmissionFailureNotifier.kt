package net.ambitious.android.mealrelay.submission

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import net.ambitious.android.mealrelay.MealRelayMainActivity
import net.ambitious.android.mealrelay.R

class MealSubmissionFailureNotifier(private val context: Context) {
  fun notifyFailure() {
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(NotificationChannel(
      CHANNEL_ID,
      context.getString(R.string.meal_submission_failure_channel),
      NotificationManager.IMPORTANCE_DEFAULT,
    ))
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MealRelayMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    notificationManager.notify(
      NOTIFICATION_ID,
      Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(context.getString(R.string.meal_submission_failure_title))
        .setContentText(context.getString(R.string.meal_submission_failure_text))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build(),
    )
  }

  companion object {
    private const val CHANNEL_ID = "meal_submission_failures"
    private const val NOTIFICATION_ID = 8
  }
}
