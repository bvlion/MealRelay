package net.ambitious.android.mealrelay.ui.settings

enum class UnusedAppRestrictionsStatus {
  DISABLED,
  ENABLED,
  UNKNOWN,
}

fun interface UnusedAppRestrictionsStatusProvider {
  fun getStatus(onStatus: (UnusedAppRestrictionsStatus) -> Unit)
}
