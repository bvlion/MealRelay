package net.ambitious.android.mealrelay.ui.settings

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidUnusedAppRestrictionsStatusProvider @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : UnusedAppRestrictionsStatusProvider {
  override fun getStatus(onStatus: (isDisabled: Boolean) -> Unit) {
    val status = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
    status.addListener({
      val isDisabled = try {
        status.get() == UnusedAppRestrictionsConstants.DISABLED
      } catch (_: Exception) {
        false
      }
      onStatus(isDisabled)
    }, ContextCompat.getMainExecutor(context))
  }
}
