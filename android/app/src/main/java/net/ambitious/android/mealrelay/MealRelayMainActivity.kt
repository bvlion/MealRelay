package net.ambitious.android.mealrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MealRelayMainActivity : ComponentActivity() {
  private lateinit var failedSubmissions: LinearLayout
  private lateinit var emptyMessage: TextView
  private var hasRequestedAuthorization = false

  private val requestNotificationPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) {}

  private val requestPhotoPermissions = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
    }
    requestAuthorizationIfNeeded()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    hasRequestedAuthorization = savedInstanceState?.getBoolean("hasRequestedAuthorization") ?: false
    setContentView(createContentView())
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
      PhotoEnrollmentWorker.enqueue(this)
      requestAuthorizationIfNeeded()
    } else {
      requestPhotoPermissions.launch(
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
      )
    }
  }

  override fun onResume() {
    super.onResume()
    refreshFailedSubmissions()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean("hasRequestedAuthorization", hasRequestedAuthorization)
    super.onSaveInstanceState(outState)
  }

  private fun createContentView(): View {
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(48, 48, 48, 48)
    }
    content.addView(TextView(this).apply {
      text = "MealRelay"
      textSize = 24f
    })
    content.addView(TextView(this).apply {
      text = "送信に失敗した食事"
      textSize = 18f
      setPadding(0, 32, 0, 16)
    })
    emptyMessage = TextView(this).apply { text = "送信に失敗した食事はありません。" }
    content.addView(emptyMessage)
    failedSubmissions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(ScrollView(this).apply { addView(failedSubmissions) },
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    return content
  }

  private fun requestAuthorizationIfNeeded() {
    if (hasRequestedAuthorization) return
    hasRequestedAuthorization = true
    lifecycleScope.launch {
      val hasToken = withContext(Dispatchers.IO) { MealRelayTokenStore(this@MealRelayMainActivity).read() != null }
      if (!hasToken) {
        startActivity(android.content.Intent(this@MealRelayMainActivity, MealRelayAuthorizationActivity::class.java))
      }
    }
  }

  private fun refreshFailedSubmissions() {
    lifecycleScope.launch {
      val submissions = withContext(Dispatchers.IO) {
        PhotoProcessingDatabase.get(this@MealRelayMainActivity).photoProcessingDao()
          .getMealSubmissionsWithState(MealSubmissionEntity.STATE_FAILED)
      }
      failedSubmissions.removeAllViews()
      emptyMessage.visibility = if (submissions.isEmpty()) View.VISIBLE else View.GONE
      submissions.forEach { submission -> failedSubmissions.addView(createFailedSubmissionView(submission)) }
    }
  }

  private fun createFailedSubmissionView(submission: MealSubmissionEntity): View {
    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      addView(TextView(this@MealRelayMainActivity).apply {
        val type = if (submission.type == MealSubmissionEntity.TYPE_IMAGE) "写真" else "テキスト"
        text = "$type: ${DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(submission.occurredAt).atZone(ZoneId.systemDefault()))}"
      }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(Button(this@MealRelayMainActivity).apply {
        text = "再送"
        setOnClickListener {
          isEnabled = false
          lifecycleScope.launch {
            withContext(Dispatchers.IO) {
              MealSubmissionManager(
                PhotoProcessingDatabase.get(this@MealRelayMainActivity).photoProcessingDao(),
                RetrofitMealSubmissionTransport(
                  this@MealRelayMainActivity,
                  MealRelayBackendClient(MealRelayTokenStore(this@MealRelayMainActivity)::read),
                ),
              ).submitManually(submission.mealId)
            }
            refreshFailedSubmissions()
          }
        }
      })
    }
  }
}
