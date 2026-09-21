package net.ambitious.android.mealrelay

import android.Manifest
import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object MealSubmissionFailureNotifier {
  private const val CHANNEL_ID = "meal_submission_failures"

  fun notify(context: Context) {
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      return
    }
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(NotificationChannel(
      CHANNEL_ID,
      "食事送信の失敗",
      NotificationManager.IMPORTANCE_DEFAULT,
    ))
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MealRelayMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    notificationManager.notify(
      8,
      Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("食事を送信できませんでした")
        .setContentText("メイン画面から再送できます。")
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build(),
    )
  }
}
