package net.ambitious.android.mealrelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.ambitious.android.mealrelay.R

@Composable
fun FailedMealSubmissionsScreen(
  state: FailedMealSubmissionsState,
  onRetry: (String) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Text(stringResource(R.string.failed_meal_submissions_title), style = MaterialTheme.typography.headlineSmall)
    if (state.submissions.isEmpty()) {
      Text(
        stringResource(R.string.failed_meal_submissions_empty),
        modifier = Modifier.padding(top = 24.dp),
      )
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
        items(state.submissions, key = { it.mealId }) { submission ->
          FailedMealSubmissionRow(submission, onRetry)
        }
      }
    }
  }
}

@Composable
private fun FailedMealSubmissionRow(submission: FailedMealSubmission, onRetry: (String) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    val type = if (submission.type == FailedMealSubmissionType.Image) {
      stringResource(R.string.meal_submission_type_image)
    } else {
      stringResource(R.string.meal_submission_type_text)
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(type)
      submission.content?.let { Text(it) }
      Text(submission.occurredAt)
    }
    Button(onClick = { onRetry(submission.mealId) }) {
      Text(stringResource(R.string.meal_submission_retry))
    }
  }
}
