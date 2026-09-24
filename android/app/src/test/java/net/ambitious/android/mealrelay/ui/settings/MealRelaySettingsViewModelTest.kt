package net.ambitious.android.mealrelay.ui.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.ArrayDeque

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MealRelaySettingsViewModelTest {
  private val application = RuntimeEnvironment.getApplication()

  private fun createViewModel(context: Context, statusProvider: FakeUnusedAppRestrictionsStatusProvider):
    MealRelaySettingsViewModel {
    return MealRelaySettingsViewModel(context, statusProvider)
  }

  @Test
  fun restrictionsAlreadyDisabledDoNotShowInitialGuide() {
    val context = SettingsPermissionContext(application, hasPhotoAccess = true, hasNotificationPermission = false)
    val statusProvider = FakeUnusedAppRestrictionsStatusProvider()
    val viewModel = createViewModel(context, statusProvider)

    viewModel.completeInitialSetupPermissionChecks()
    statusProvider.complete(UnusedAppRestrictionsStatus.DISABLED)

    assertTrue(viewModel.state.value.isUnusedAppRestrictionDisabled)
    assertFalse(viewModel.state.value.shouldShowUnusedAppRestrictionsGuide)
  }

  @Test
  fun deniedOptionalNotificationDoesNotPreventGuideAfterPhotoAccess() {
    val context = SettingsPermissionContext(application, hasPhotoAccess = true, hasNotificationPermission = false)
    val statusProvider = FakeUnusedAppRestrictionsStatusProvider()
    val viewModel = createViewModel(context, statusProvider)

    viewModel.completeInitialSetupPermissionChecks()
    statusProvider.complete(UnusedAppRestrictionsStatus.ENABLED)

    assertTrue(viewModel.state.value.hasFullPhotoAccess)
    assertFalse(viewModel.state.value.hasNotificationPermission)
    assertTrue(viewModel.state.value.shouldShowUnusedAppRestrictionsGuide)
  }

  @Test
  fun guideWaitsUntilFullPhotoAccessIsGranted() {
    val context = SettingsPermissionContext(application, hasPhotoAccess = false, hasNotificationPermission = true)
    val statusProvider = FakeUnusedAppRestrictionsStatusProvider()
    val viewModel = createViewModel(context, statusProvider)

    viewModel.completeInitialSetupPermissionChecks()
    statusProvider.complete(UnusedAppRestrictionsStatus.ENABLED)

    assertFalse(viewModel.state.value.hasFullPhotoAccess)
    assertFalse(viewModel.state.value.shouldShowUnusedAppRestrictionsGuide)
  }

  @Test
  fun unknownRestrictionsStatusDoesNotShowInitialGuide() {
    val context = SettingsPermissionContext(application, hasPhotoAccess = true, hasNotificationPermission = false)
    val statusProvider = FakeUnusedAppRestrictionsStatusProvider()
    val viewModel = createViewModel(context, statusProvider)

    viewModel.completeInitialSetupPermissionChecks()
    statusProvider.complete(UnusedAppRestrictionsStatus.UNKNOWN)

    assertFalse(viewModel.state.value.isUnusedAppRestrictionDisabled)
    assertFalse(viewModel.state.value.shouldShowUnusedAppRestrictionsGuide)
  }

  @Test
  fun laterRefreshReflectsPermissionChangesWithoutChangingNotificationRequirement() {
    val context = SettingsPermissionContext(application, hasPhotoAccess = false, hasNotificationPermission = false)
    val statusProvider = FakeUnusedAppRestrictionsStatusProvider()
    val viewModel = createViewModel(context, statusProvider)
    viewModel.completeInitialSetupPermissionChecks()
    statusProvider.complete(UnusedAppRestrictionsStatus.ENABLED)
    context.hasPhotoAccess = true

    viewModel.refresh()
    statusProvider.complete(UnusedAppRestrictionsStatus.ENABLED)

    assertTrue(viewModel.state.value.hasFullPhotoAccess)
    assertFalse(viewModel.state.value.hasNotificationPermission)
    assertTrue(viewModel.state.value.shouldShowUnusedAppRestrictionsGuide)
  }


}

private class SettingsPermissionContext(
  base: Context,
  var hasPhotoAccess: Boolean,
  var hasNotificationPermission: Boolean,
) : ContextWrapper(base) {
  override fun checkSelfPermission(permission: String): Int = when (permission) {
    Manifest.permission.READ_MEDIA_IMAGES -> permissionResult(hasPhotoAccess)
    Manifest.permission.POST_NOTIFICATIONS -> permissionResult(hasNotificationPermission)
    else -> PackageManager.PERMISSION_DENIED
  }

  private fun permissionResult(isGranted: Boolean): Int =
    if (isGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
}

private class FakeUnusedAppRestrictionsStatusProvider : UnusedAppRestrictionsStatusProvider {
  private val pendingCallbacks = ArrayDeque<(UnusedAppRestrictionsStatus) -> Unit>()

  override fun getStatus(onStatus: (UnusedAppRestrictionsStatus) -> Unit) {
    pendingCallbacks.addLast(onStatus)
  }

  fun complete(status: UnusedAppRestrictionsStatus) {
    pendingCallbacks.removeFirst().invoke(status)
  }
}
