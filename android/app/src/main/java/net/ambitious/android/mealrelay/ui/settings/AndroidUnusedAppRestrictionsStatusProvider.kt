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
  override fun getStatus(onStatus: (UnusedAppRestrictionsStatus) -> Unit) {
    val status = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
    status.addListener({
      val restrictionsStatus = try {
        when (status.get()) {
          UnusedAppRestrictionsConstants.DISABLED -> UnusedAppRestrictionsStatus.DISABLED
          UnusedAppRestrictionsConstants.API_30_BACKPORT,
          UnusedAppRestrictionsConstants.API_30,
          UnusedAppRestrictionsConstants.API_31 -> UnusedAppRestrictionsStatus.ENABLED
          else -> UnusedAppRestrictionsStatus.UNKNOWN
        }
      } catch (_: Exception) {
        UnusedAppRestrictionsStatus.UNKNOWN
      }
      onStatus(restrictionsStatus)
    }, ContextCompat.getMainExecutor(context))
  }
}
