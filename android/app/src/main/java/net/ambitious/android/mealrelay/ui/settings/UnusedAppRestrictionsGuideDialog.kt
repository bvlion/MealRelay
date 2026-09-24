package net.ambitious.android.mealrelay.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.ambitious.android.mealrelay.R

@Composable
fun UnusedAppRestrictionsGuideDialog(
  isVisible: Boolean,
  onDismiss: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  if (!isVisible) return

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.unused_app_restrictions_title)) },
    text = { Text(stringResource(R.string.unused_app_restrictions_explanation)) },
    confirmButton = {
      TextButton(onClick = onOpenSettings) {
        Text(stringResource(R.string.unused_app_restrictions_open_settings))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.unused_app_restrictions_later))
      }
    },
  )
}
