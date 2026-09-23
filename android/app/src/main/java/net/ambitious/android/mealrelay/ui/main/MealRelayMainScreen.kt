package net.ambitious.android.mealrelay.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.ambitious.android.mealrelay.R
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsScreen
import net.ambitious.android.mealrelay.ui.FailedMealSubmissionsState

@Composable
fun MealRelayMainScreen(
  failedMealSubmissionsState: FailedMealSubmissionsState,
  onRetryFailedMeal: (String) -> Unit,
  onSubmitManualMeal: (String) -> Unit,
) {
  var isManualMealEntryDialogVisible by remember { mutableStateOf(false) }
  Column(modifier = Modifier.fillMaxSize()) {
    Button(
      onClick = { isManualMealEntryDialogVisible = true },
      modifier = Modifier.padding(24.dp),
    ) {
      Text(stringResource(R.string.manual_meal_entry_start))
    }
    FailedMealSubmissionsScreen(
      state = failedMealSubmissionsState,
      onRetry = onRetryFailedMeal,
      modifier = Modifier.weight(1f),
    )
  }
  if (isManualMealEntryDialogVisible) {
    ManualMealEntryDialog(
      onDismiss = { isManualMealEntryDialogVisible = false },
      onSubmit = onSubmitManualMeal,
    )
  }
}
