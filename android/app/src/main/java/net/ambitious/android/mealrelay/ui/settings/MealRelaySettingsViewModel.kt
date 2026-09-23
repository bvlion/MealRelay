package net.ambitious.android.mealrelay.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
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
  val isUnusedAppRestrictionDisabled: Boolean = false,
  val shouldShowUnusedAppRestrictionsGuide: Boolean = false,
)

@HiltViewModel
class MealRelaySettingsViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val mutableState = MutableStateFlow(MealRelaySettingsState())
  val state: StateFlow<MealRelaySettingsState> = mutableState.asStateFlow()
  private var hasEvaluatedInitialSetup = false
  private var hasPendingUnusedAppRestrictionsGuide = false
  private var refreshGeneration = 0

  fun refresh() {
    val hasFullPhotoAccess = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
      PackageManager.PERMISSION_GRANTED
    val hasNotificationPermission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
    if (!hasEvaluatedInitialSetup && hasFullPhotoAccess && hasNotificationPermission) {
      hasEvaluatedInitialSetup = true
      hasPendingUnusedAppRestrictionsGuide = true
    }
    val generation = ++refreshGeneration
    val updatedState = mutableState.value.copy(
      hasFullPhotoAccess = hasFullPhotoAccess,
      hasNotificationPermission = hasNotificationPermission,
    )
    mutableState.value = updatedState
    val unusedAppRestrictionsStatus = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
    unusedAppRestrictionsStatus.addListener({
      if (generation != refreshGeneration) return@addListener
      val isUnusedAppRestrictionDisabled = try {
        unusedAppRestrictionsStatus.get() == UnusedAppRestrictionsConstants.DISABLED
      } catch (_: Exception) {
        false
      }
      mutableState.value = mutableState.value.copy(
        isUnusedAppRestrictionDisabled = isUnusedAppRestrictionDisabled,
        shouldShowUnusedAppRestrictionsGuide = mutableState.value.shouldShowUnusedAppRestrictionsGuide ||
          hasPendingUnusedAppRestrictionsGuide,
      )
    }, ContextCompat.getMainExecutor(context))
  }

  fun dismissUnusedAppRestrictionsGuide() {
    hasPendingUnusedAppRestrictionsGuide = false
    mutableState.value = mutableState.value.copy(shouldShowUnusedAppRestrictionsGuide = false)
  }
}
