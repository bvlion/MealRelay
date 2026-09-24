package net.ambitious.android.mealrelay.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class MealRelaySettingsState(
  val hasFullPhotoAccess: Boolean = false,
  val hasNotificationPermission: Boolean = false,
  val unusedAppRestrictionsStatus: UnusedAppRestrictionsStatus = UnusedAppRestrictionsStatus.UNKNOWN,
  val shouldShowUnusedAppRestrictionsGuide: Boolean = false,
) {
  val isUnusedAppRestrictionDisabled: Boolean
    get() = unusedAppRestrictionsStatus == UnusedAppRestrictionsStatus.DISABLED
}

@HiltViewModel
class MealRelaySettingsViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val unusedAppRestrictionsStatusProvider: UnusedAppRestrictionsStatusProvider,
) : ViewModel() {
  private val mutableState = MutableStateFlow(MealRelaySettingsState())
  val state: StateFlow<MealRelaySettingsState> = mutableState.asStateFlow()
  private var hasCompletedInitialPermissionChecks = false
  private var hasDismissedUnusedAppRestrictionsGuide = false
  private var refreshGeneration = 0

  fun refresh() {
    val hasFullPhotoAccess = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
      PackageManager.PERMISSION_GRANTED
    val hasNotificationPermission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
    val generation = ++refreshGeneration
    mutableState.value = mutableState.value.copy(
      hasFullPhotoAccess = hasFullPhotoAccess,
      hasNotificationPermission = hasNotificationPermission,
    )
    unusedAppRestrictionsStatusProvider.getStatus { unusedAppRestrictionsStatus ->
      if (generation != refreshGeneration) return@getStatus
      mutableState.value = mutableState.value.copy(
        unusedAppRestrictionsStatus = unusedAppRestrictionsStatus,
        shouldShowUnusedAppRestrictionsGuide = hasCompletedInitialPermissionChecks &&
          hasFullPhotoAccess &&
          unusedAppRestrictionsStatus == UnusedAppRestrictionsStatus.ENABLED &&
          !hasDismissedUnusedAppRestrictionsGuide,
      )
    }
  }

  fun completeInitialSetupPermissionChecks() {
    hasCompletedInitialPermissionChecks = true
    refresh()
  }

  fun dismissUnusedAppRestrictionsGuide() {
    hasDismissedUnusedAppRestrictionsGuide = true
    mutableState.value = mutableState.value.copy(shouldShowUnusedAppRestrictionsGuide = false)
  }
}
