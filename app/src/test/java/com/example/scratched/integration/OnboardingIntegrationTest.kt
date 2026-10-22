package com.example.scratched.integration

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelStore
import com.example.scratched.*
import com.example.scratched.meshMVP.transport.BluetoothGattClientManager
import com.example.scratched.onboarding.*
import com.example.scratched.support.*
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.utilities.stateManagers.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Real coordinator, state managers, preferences, permissions and ViewModel.
 * Only radio adapters and the unrelated scanner are test doubles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 35])
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingIntegrationTest {
    @get:Rule val main = MainDispatcherRule()
    private lateinit var app: Application
    private lateinit var permissions: PermissionsManager
    private lateinit var repository: OnboardingPrefsRepository
    private lateinit var bluetooth: BluetoothStateManager
    private lateinit var location: LocationStateManager
    private lateinit var coordinator: OnboardingFlowCoordinator
    private val radio = FakeBluetoothAdapter()
    private val gps = FakeLocationAdapter()
    private val store = ViewModelStore()

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("scratch_onboarding", Context.MODE_PRIVATE).edit().clear().commit()
        permissions = PermissionsManager(app)
        shadowOf(app).grantPermissions(*(permissions.getRequiredPermissions() + permissions.getOptionalPermissions()).toTypedArray())
        // ContextCompat's pre-33 receiver guard uses the app's own signature permission.
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        repository = OnboardingPrefsRepository(app)
        bluetooth = BluetoothStateManager(app, radio)
        location = LocationStateManager(gps)
        coordinator = OnboardingFlowCoordinator(permissions, bluetooth, location, repository)
    }
    @After fun cleanup() { store.clear(); bluetooth.stopMonitoring() }

    private fun createViewModel(): MainViewModel {
        val scanner = mockk<BluetoothGattClientManager>(relaxed = true)
        every { scanner.discoveredDevices } returns MutableSharedFlow()
        return MainViewModel(coordinator, bluetooth, location, permissions, scanner).also { store.put("main", it) }
    }
    @Test fun firstLaunch_completesAndPersistsForNewCoordinator() {
        val vm = createViewModel()
        assertEquals(OnboardingState.WELCOME, vm.onboardingState.value)
        vm.onNextClicked()
        assertEquals(OnboardingState.ABOUT, vm.onboardingState.value)
        vm.onNextClicked()
        assertEquals(OnboardingState.ALL_SET, vm.onboardingState.value)
        vm.onNextClicked()
        assertTrue(OnboardingPrefsRepository(app).isCompleted())
        val reopened = OnboardingFlowCoordinator(permissions, bluetooth, location, OnboardingPrefsRepository(app))
        reopened.startAppFlow()
        assertEquals(OnboardingState.COMPLETED, reopened.currentState.value)
    }
    @Test fun permissionRequest_travelsFromCoordinatorThroughViewModel() = runTest(main.dispatcher) {
        val denied = permissions.getBluetoothPermissions().last()
        shadowOf(app).denyPermissions(denied)
        val vm = createViewModel()
        vm.onNextClicked(); vm.onNextClicked()
        assertEquals(OnboardingState.BLUETOOTH, vm.onboardingState.value)
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(1000) { vm.activityEvents.first() } }
        vm.onRequestPermissionsClicked()
        assertEquals(ActivityEvent.RequestPermissions(arrayOf(denied)), event.await())
        shadowOf(app).grantPermissions(denied)
        vm.onPermissionsResult(mapOf(denied to true))
        assertEquals(OnboardingState.ALL_SET, vm.onboardingState.value)
    }
    @Test fun criticalPermissionDenialAndManualGrant_recoverThroughRealManagers() {
        val denied = Manifest.permission.ACCESS_FINE_LOCATION
        shadowOf(app).denyPermissions(denied)
        val vm = createViewModel()
        vm.onNextClicked(); vm.onNextClicked()
        vm.onPermissionsResult(mapOf(denied to false))
        assertEquals(OnboardingState.RUQUIRED_PERMISSION_REJECTED, vm.onboardingState.value)
        shadowOf(app).grantPermissions(denied)
        vm.onNextClicked()
        assertEquals(OnboardingState.ALL_SET, vm.onboardingState.value)
    }
    @Test fun locationSettingsResult_unblocksOnboarding() = runTest(main.dispatcher) {
        gps.enabled = false
        gps.enableRequest = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        val vm = createViewModel()
        vm.onNextClicked(); vm.onNextClicked()
        assertEquals(OnboardingState.ENABLE_LOCATION, vm.onboardingState.value)
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(1000) { vm.activityEvents.first() } }
        vm.onEnableLocationClicked()
        assertEquals(gps.enableRequest, (event.await() as ActivityEvent.EnableLocation).intent)
        gps.enabled = true
        vm.onLocationSettingsClosed()
        assertEquals(OnboardingState.ALL_SET, vm.onboardingState.value)
    }
    @Test fun bluetoothBroadcast_reachesCoordinatorThroughViewModel() {
        repository.markComplete()
        val vm = createViewModel()
        assertEquals(OnboardingState.COMPLETED, vm.onboardingState.value)
        radio.currentState = BluetoothState.DISABLED
        app.sendBroadcast(Intent(BluetoothAdapter.ACTION_STATE_CHANGED))
        ShadowLooper.idleMainLooper()
        assertEquals(BluetoothState.DISABLED, bluetooth.bluetoothStateFlow.value)
        assertEquals(OnboardingState.ENABLE_BLUETOOTH, vm.onboardingState.value)
        radio.currentState = BluetoothState.ENABLED
        vm.onBluetoothEnableResult(true)
        assertEquals(OnboardingState.COMPLETED, vm.onboardingState.value)
    }
    @Test fun stoppedMonitoring_noLongerReceivesBroadcasts() {
        bluetooth.startMonitoring()
        bluetooth.stopMonitoring()
        radio.currentState = BluetoothState.DISABLED
        app.sendBroadcast(Intent(BluetoothAdapter.ACTION_STATE_CHANGED))
        ShadowLooper.idleMainLooper()
        assertEquals(BluetoothState.ENABLED, bluetooth.bluetoothStateFlow.value)
    }
    @Test fun restartingOnboarding_resetsRealPreferences() {
        repository.markComplete()
        val vm = createViewModel()
        vm.onRestartOnboardingClick()
        assertFalse(repository.isCompleted())
        assertEquals(OnboardingState.WELCOME, vm.onboardingState.value)
    }
}
