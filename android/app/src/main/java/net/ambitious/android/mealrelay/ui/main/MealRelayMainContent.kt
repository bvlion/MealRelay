package net.ambitious.android.mealrelay.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsState
import net.ambitious.android.mealrelay.ui.settings.MealRelaySettingsScreen
import net.ambitious.android.mealrelay.ui.settings.MealRelaySettingsState
import net.ambitious.android.mealrelay.ui.settings.UnusedAppRestrictionsGuideDialog

@Composable
fun MealRelayMainContent(
  failedMealSubmissionsState: FailedMealSubmissionsState,
  authorizationState: StateFlow<MainAuthorizationState>,
  settingsState: StateFlow<MealRelaySettingsState>,
  onRetryFailedMeal: (String) -> Unit,
  onSubmitManualMeal: (String) -> Unit,
  onRequestPhotoPermission: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onOpenPhotoApplicationSettings: () -> Unit,
  onOpenNotificationApplicationSettings: () -> Unit,
  onOpenUnusedAppRestrictionsSettings: () -> Unit,
  onDismissUnusedAppRestrictionsGuide: () -> Unit,
  onStartAuthorization: () -> Unit,
) {
  val currentAuthorizationState by authorizationState.collectAsState()
  val currentSettingsState by settingsState.collectAsState()
  var isSettingsVisible by rememberSaveable { mutableStateOf(false) }

  BackHandler(enabled = isSettingsVisible) {
    isSettingsVisible = false
  }

  LaunchedEffect(currentAuthorizationState) {
    if (currentAuthorizationState == MainAuthorizationState.Required) {
      onStartAuthorization()
    }
  }

  if (isSettingsVisible) {
    MealRelaySettingsScreen(
      state = currentSettingsState,
      onBack = { isSettingsVisible = false },
      onRequestPhotoPermission = onRequestPhotoPermission,
      onOpenPhotoApplicationSettings = onOpenPhotoApplicationSettings,
      onRequestNotificationPermission = onRequestNotificationPermission,
      onOpenNotificationApplicationSettings = onOpenNotificationApplicationSettings,
      onOpenUnusedAppRestrictions = onOpenUnusedAppRestrictionsSettings,
    )
  } else {
    MealRelayMainScreen(
      failedMealSubmissionsState = failedMealSubmissionsState,
      onRetryFailedMeal = onRetryFailedMeal,
      onSubmitManualMeal = onSubmitManualMeal,
      onOpenSettings = { isSettingsVisible = true },
    )
  }

  UnusedAppRestrictionsGuideDialog(
    isVisible = currentSettingsState.shouldShowUnusedAppRestrictionsGuide,
    onDismiss = onDismissUnusedAppRestrictionsGuide,
    onOpenSettings = {
      onDismissUnusedAppRestrictionsGuide()
      onOpenUnusedAppRestrictionsSettings()
    },
  )
}
