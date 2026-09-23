package net.ambitious.android.mealrelay.notification

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject

class FirebaseMessagingRegistration @Inject constructor() {
  fun register() {
    FirebaseMessaging.getInstance().register()
  }
}
