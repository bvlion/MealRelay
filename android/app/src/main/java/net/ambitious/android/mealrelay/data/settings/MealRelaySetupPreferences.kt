package net.ambitious.android.mealrelay.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRelaySetupPreferences @Inject constructor(
  @ApplicationContext context: Context,
) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun hasRequestedInitialNotificationPermission(): Boolean =
    preferences.getBoolean(KEY_INITIAL_NOTIFICATION_PERMISSION_REQUESTED, false)

  fun markInitialNotificationPermissionRequested() {
    preferences.edit()
      .putBoolean(KEY_INITIAL_NOTIFICATION_PERMISSION_REQUESTED, true)
      .apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "mealrelay_setup"
    const val KEY_INITIAL_NOTIFICATION_PERMISSION_REQUESTED = "initial_notification_permission_requested"
  }
}
