package net.ambitious.android.mealrelay.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.ambitious.android.mealrelay.R

@Composable
fun ManualMealEntryDialog(
  onDismiss: () -> Unit,
  onSubmit: (String) -> Unit,
) {
  var text by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.manual_meal_entry_title)) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.manual_meal_entry_placeholder)) },
        minLines = 3,
      )
    },
    confirmButton = {
      Button(
        onClick = {
          onSubmit(text)
          onDismiss()
        },
        enabled = text.isNotBlank(),
      ) {
        Text(stringResource(R.string.manual_meal_entry_submit))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.manual_meal_entry_cancel))
      }
    },
  )
}
