package net.ambitious.android.mealrelay.ui.settings

fun interface UnusedAppRestrictionsStatusProvider {
  fun getStatus(onStatus: (isDisabled: Boolean) -> Unit)
}
