package com.example.scratched.integration

import android.app.Application
import android.app.Activity
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.activity.result.contract.ActivityResultContracts
import com.example.scratched.MainActivity
import com.example.scratched.MainViewModel
import com.example.scratched.onboarding.OnboardingPrefsRepository
import com.example.scratched.utilities.PermissionsManager
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowToast

/** Real Activity, Compose screens, ViewModel, coordinator, managers and storage.
 * Android system services are simulated by Robolectric, not by production-class mocks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var app: Application
    private lateinit var permissions: PermissionsManager
    private var controller: ActivityController<MainActivity>? = null

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("scratch_onboarding", Context.MODE_PRIVATE).edit().clear().commit()
        permissions = PermissionsManager(app)
        shadowOf(app).grantPermissions(*(permissions.getRequiredPermissions() + permissions.getOptionalPermissions()).toTypedArray())
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, true)
        val bluetooth = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(bluetooth).setEnabled(true)
        shadowOf(app.getSystemService(Context.LOCATION_SERVICE) as LocationManager).setLocationEnabled(true)
    }
    @After fun cleanup() { controller?.pause()?.stop()?.destroy() }
    private fun launch(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().also { controller = it }.get()
    private fun viewModel(activity: MainActivity) = ViewModelProvider(activity)[MainViewModel::class.java]

    @Test fun welcomeButton_navigatesThroughRealViewModelAndCoordinator() {
        launch()
        compose.onNodeWithText("Welcome!").assertIsDisplayed()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("A little about the app").assertIsDisplayed()
        compose.onNodeWithText("2 of 6").assertExists()
    }
    @Test fun firstLaunch_reachesMainScreenAndSavesCompletion() {
        launch()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Start using the app").performClick()
        compose.onNodeWithText("Start scanning").assertIsEnabled()
        assertTrue(OnboardingPrefsRepository(app).isCompleted())
    }
    @Test fun returningUser_canRestartOnboardingFromMainScreen() {
        OnboardingPrefsRepository(app).markComplete()
        launch()
        compose.onNodeWithText("Run onboarding again").performClick()
        compose.onNodeWithText("Welcome!").assertIsDisplayed()
        assertFalse(OnboardingPrefsRepository(app).isCompleted())
    }
    @Test fun rejectionScreen_opensThisApplicationsSettings() {
        val activity = launch()
        val denied = permissions.getRequiredPermissions().first()
        shadowOf(app).denyPermissions(denied)
        compose.runOnIdle { viewModel(activity).onPermissionsResult(mapOf(denied to false)) }
        compose.onNodeWithText("Grant permissions manually").performClick()
        val request = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, request.action)
        assertEquals("package:${activity.packageName}", request.data.toString())
        assertTrue(request.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
    @Test fun enableLocationEvent_opensSystemLocationSettings() {
        val activity = launch()
        compose.runOnIdle { viewModel(activity).onEnableLocationClicked() }
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, request.intent.action)
    }
    @Test fun enableBluetoothEvent_opensSystemEnableRequest() {
        val activity = launch()
        compose.runOnIdle { viewModel(activity).onEnableBluetoothClicked() }
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE, request.intent.action)
    }
    @Test fun deniedPermission_isRequestedThroughActivityResultLauncher() {
        val denied = permissions.getBluetoothPermissions().first()
        shadowOf(app).denyPermissions(denied)
        val activity = launch()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Allow Bluetooth").performClick()
        assertArrayEquals(arrayOf(denied), shadowOf(activity).lastRequestedPermission.requestedPermissions)
    }

    @Test fun bluetoothEnableResult_advancesToMainScreenForReturningUser() {
        OnboardingPrefsRepository(app).markComplete()
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(false)
        val activity = launch()
        compose.onNodeWithText("Turn on Bluetooth").performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        compose.runOnIdle {
            shadowOf(adapter).setEnabled(true)
            activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, null)
        }
        compose.onNodeWithText("Start scanning").assertExists()
    }

    @Test fun bluetoothEnableCancelled_keepsOnboardingAndShowsExplanation() {
        OnboardingPrefsRepository(app).markComplete()
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(false)
        val activity = launch()
        compose.onNodeWithText("Turn on Bluetooth").performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        compose.runOnIdle {
            activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_CANCELED, null)
        }
        compose.onNodeWithText("Turn on Bluetooth").assertExists()
        assertEquals("User declined/Error happened: Bluetooth is required", ShadowToast.getTextOfLatestToast())
    }

    @Test fun locationSettingsResult_rechecksServiceAndAdvances() {
        val location = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(location).setLocationEnabled(false)
        val activity = launch()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Turn on location").performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        compose.runOnIdle {
            shadowOf(location).setLocationEnabled(true)
            // Location settings has no meaningful RESULT_OK contract: the real state is checked.
            activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_CANCELED, null)
        }
        compose.onNodeWithText("You're all set!").assertIsDisplayed()
    }

    @Test fun permissionDenialResult_displaysManualRecoveryScreen() {
        val denied = permissions.getBluetoothPermissions().first()
        shadowOf(app).denyPermissions(denied)
        val activity = launch()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Allow Bluetooth").performClick()
        val request = shadowOf(activity).lastRequestedPermission
        compose.runOnIdle {
            activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK,
                Intent().putExtra(ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS, request.requestedPermissions)
                    .putExtra(ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                        intArrayOf(PackageManager.PERMISSION_DENIED)))
        }
        compose.onNodeWithText("Grant permissions manually").assertIsDisplayed()
    }

    @Test fun optionalNotifications_canBeSkippedWithoutBlockingMainScreen() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        launch()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Start using the app").performClick()
        compose.onNodeWithText("Start scanning").assertIsEnabled()
    }

    @Test fun resumingAfterPermissionRevocation_refreshesVisibleAvailability() {
        OnboardingPrefsRepository(app).markComplete()
        launch()
        compose.onNodeWithText("Start scanning").assertIsEnabled()
        compose.runOnIdle {
            controller!!.pause().stop()
            shadowOf(app).denyPermissions(permissions.getRequiredPermissions().first())
            controller!!.start().resume().visible()
        }
        compose.onNodeWithText("Start scanning").assertIsNotEnabled()
    }

    @Test fun activityDestroy_stopsRegisteredRadioScan() {
        OnboardingPrefsRepository(app).markComplete()
        launch()
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = shadowOf(adapter.bluetoothLeScanner)
        compose.onNodeWithText("Start scanning").performClick()
        assertEquals(1, scanner.scanCallbacks.size)
        compose.runOnIdle {
            controller!!.pause().stop().destroy()
            controller = null
        }
        assertTrue(scanner.scanCallbacks.isEmpty())
    }
}
