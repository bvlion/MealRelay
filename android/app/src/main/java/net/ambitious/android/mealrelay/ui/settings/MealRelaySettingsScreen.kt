package net.ambitious.android.mealrelay.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.ambitious.android.mealrelay.R

@Composable
fun MealRelaySettingsScreen(
  state: MealRelaySettingsState,
  onBack: () -> Unit,
  onRequestPhotoPermission: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onOpenUnusedAppRestrictions: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(stringResource(R.string.settings_title))
    SettingRow(
      title = stringResource(R.string.settings_photo_access),
      isConfigured = state.hasFullPhotoAccess,
      onClick = onRequestPhotoPermission,
    )
    SettingRow(
      title = stringResource(R.string.settings_notification_permission),
      isConfigured = state.hasNotificationPermission,
      onClick = onRequestNotificationPermission,
    )
    SettingRow(
      title = stringResource(R.string.settings_unused_app_restrictions),
      isConfigured = state.isUnusedAppRestrictionDisabled,
      onClick = onOpenUnusedAppRestrictions,
    )
    Button(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
  }
}

@Composable
private fun SettingRow(
  title: String,
  isConfigured: Boolean,
  onClick: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title)
    Text(
      stringResource(
        if (isConfigured) R.string.settings_configured else R.string.settings_not_configured,
      ),
    )
    if (!isConfigured) {
      Button(onClick = onClick) {
        Text(stringResource(R.string.settings_configure))
      }
    }
  }
}
