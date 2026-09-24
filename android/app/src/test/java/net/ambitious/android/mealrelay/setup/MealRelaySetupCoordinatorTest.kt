package net.ambitious.android.mealrelay.setup

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import net.ambitious.android.mealrelay.data.settings.MealRelaySetupPreferences
import net.ambitious.android.mealrelay.photo.PhotoEnrollmentScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MealRelaySetupCoordinatorTest {
  private val application = RuntimeEnvironment.getApplication()

  @Test
  fun initialSetupRequestsNotificationOnceThenCompletesPhotoSetupAfterPhotoCheck() {
    val permissionContext = SetupPermissionContext(
      application,
      hasFullPhotoAccess = false,
      hasNotificationPermission = false,
    )
    val photoEnrollmentScheduler = FakePhotoEnrollmentScheduler()
    val coordinator = createCoordinator(permissionContext, photoEnrollmentScheduler)
    val actions = CountingInitialSetupActions()

    coordinator.startInitialSetup(actions.actions)

    assertEquals(1, actions.notificationRequests)
    assertEquals(0, actions.photoPermissionRequests)

    coordinator.onInitialNotificationPermissionRequestCompleted(actions.actions)

    assertEquals(1, actions.photoPermissionRequests)
    assertEquals(0, actions.authorizationRequests)
    assertEquals(0, actions.completedPermissionChecks)

    permissionContext.hasFullPhotoAccess = true
    coordinator.onInitialPhotoPermissionRequestCompleted(actions.actions)

    assertEquals(1, photoEnrollmentScheduler.enqueueCount)
    assertEquals(1, actions.authorizationRequests)
    assertEquals(1, actions.completedPermissionChecks)

    createCoordinator(permissionContext, photoEnrollmentScheduler, clearSetupPreferences = false)
      .startInitialSetup(actions.actions)

    assertEquals(1, actions.notificationRequests)
  }

  @Test
  fun alreadyGrantedNotificationSkipsPromptAndContinuesToPhotoAccess() {
    val permissionContext = SetupPermissionContext(
      application,
      hasFullPhotoAccess = false,
      hasNotificationPermission = true,
    )
    val coordinator = createCoordinator(permissionContext, FakePhotoEnrollmentScheduler())
    val actions = CountingInitialSetupActions()

    coordinator.startInitialSetup(actions.actions)

    assertEquals(0, actions.notificationRequests)
    assertEquals(1, actions.photoPermissionRequests)
  }

  @Test
  fun deniedPhotoPermissionCompletesInitialSetupWithoutEnrollingPhotos() {
    val permissionContext = SetupPermissionContext(
      application,
      hasFullPhotoAccess = false,
      hasNotificationPermission = false,
    )
    val photoEnrollmentScheduler = FakePhotoEnrollmentScheduler()
    val coordinator = createCoordinator(permissionContext, photoEnrollmentScheduler)
    val actions = CountingInitialSetupActions()

    coordinator.onInitialPhotoPermissionRequestCompleted(actions.actions)

    assertEquals(0, photoEnrollmentScheduler.enqueueCount)
    assertEquals(1, actions.authorizationRequests)
    assertEquals(1, actions.completedPermissionChecks)
  }

  @Test
  fun settingsPhotoPermissionOperationOnlyRequestsPhotoAndStartsEnrollmentAfterGrant() {
    val permissionContext = SetupPermissionContext(
      application,
      hasFullPhotoAccess = false,
      hasNotificationPermission = false,
    )
    val photoEnrollmentScheduler = FakePhotoEnrollmentScheduler()
    val coordinator = createCoordinator(permissionContext, photoEnrollmentScheduler)
    var photoPermissionRequests = 0
    var settingsRefreshes = 0

    coordinator.requestSettingsPhotoPermission(
      requestPhotoPermission = { photoPermissionRequests++ },
      refreshSettings = { settingsRefreshes++ },
    )

    assertEquals(1, photoPermissionRequests)
    assertEquals(0, settingsRefreshes)

    permissionContext.hasFullPhotoAccess = true
    coordinator.onSettingsPhotoAccessChanged { settingsRefreshes++ }

    assertEquals(1, photoEnrollmentScheduler.enqueueCount)
    assertEquals(1, settingsRefreshes)
  }

  @Test
  fun settingsNotificationOperationOnlyRequestsNotification() {
    val permissionContext = SetupPermissionContext(
      application,
      hasFullPhotoAccess = false,
      hasNotificationPermission = false,
    )
    val coordinator = createCoordinator(permissionContext, FakePhotoEnrollmentScheduler())
    var notificationPermissionRequests = 0
    var settingsRefreshes = 0

    coordinator.requestSettingsNotificationPermission(
      requestNotificationPermission = { notificationPermissionRequests++ },
      refreshSettings = { settingsRefreshes++ },
    )

    assertEquals(1, notificationPermissionRequests)
    assertEquals(0, settingsRefreshes)
  }

  @Test
  fun appSettingsPhotoReturnEnqueuesOnlyWhenFullPhotoAccessIsGranted() {
    val permissionContext = SetupPermissionContext(
      application,
      hasFullPhotoAccess = false,
      hasNotificationPermission = false,
    )
    val photoEnrollmentScheduler = FakePhotoEnrollmentScheduler()
    val coordinator = createCoordinator(permissionContext, photoEnrollmentScheduler)
    var settingsRefreshes = 0

    coordinator.onSettingsPhotoAccessChanged { settingsRefreshes++ }

    assertEquals(0, photoEnrollmentScheduler.enqueueCount)
    assertEquals(1, settingsRefreshes)

    permissionContext.hasFullPhotoAccess = true
    coordinator.onSettingsPhotoAccessChanged { settingsRefreshes++ }

    assertEquals(1, photoEnrollmentScheduler.enqueueCount)
    assertEquals(2, settingsRefreshes)
  }

  private fun createCoordinator(
    context: Context,
    photoEnrollmentScheduler: PhotoEnrollmentScheduler,
    clearSetupPreferences: Boolean = true,
  ): MealRelaySetupCoordinator {
    if (clearSetupPreferences) {
      application.getSharedPreferences("mealrelay_setup", Context.MODE_PRIVATE).edit().clear().commit()
    }
    return MealRelaySetupCoordinator(context, MealRelaySetupPreferences(application), photoEnrollmentScheduler)
  }
}

private class CountingInitialSetupActions {
  var notificationRequests = 0
  var photoPermissionRequests = 0
  var authorizationRequests = 0
  var completedPermissionChecks = 0

  val actions = InitialSetupActions(
    requestNotificationPermission = { notificationRequests++ },
    requestPhotoPermission = { photoPermissionRequests++ },
    requestAuthorization = { authorizationRequests++ },
    completePermissionChecks = { completedPermissionChecks++ },
  )
}

private class SetupPermissionContext(
  base: Context,
  var hasFullPhotoAccess: Boolean,
  var hasNotificationPermission: Boolean,
) : ContextWrapper(base) {
  override fun checkSelfPermission(permission: String): Int = when (permission) {
    Manifest.permission.READ_MEDIA_IMAGES -> permissionResult(hasFullPhotoAccess)
    Manifest.permission.POST_NOTIFICATIONS -> permissionResult(hasNotificationPermission)
    else -> PackageManager.PERMISSION_DENIED
  }

  private fun permissionResult(isGranted: Boolean): Int =
    if (isGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
}

private class FakePhotoEnrollmentScheduler : PhotoEnrollmentScheduler {
  var enqueueCount = 0
    private set

  override fun enqueue() {
    enqueueCount++
  }
}
