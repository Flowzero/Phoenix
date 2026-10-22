package com.example.scratched.regressions

import android.app.Application
import android.bluetooth.le.ScanCallback
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.scratched.*
import com.example.scratched.onboarding.*
import com.example.scratched.support.*
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.utilities.stateManagers.*
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Expected behavior, not characterization of bugs. These tests stay active in the full suite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class KnownDefectsRegressionTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private lateinit var app: Application
    private lateinit var permissions: PermissionsManager
    private lateinit var repository: OnboardingPrefsRepository
    private val radio = FakeBluetoothAdapter()
    private val gps = FakeLocationAdapter()

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("scratch_onboarding", Context.MODE_PRIVATE).edit().clear().commit()
        permissions = PermissionsManager(app)
        shadowOf(app).grantPermissions(*(permissions.getRequiredPermissions() + permissions.getOptionalPermissions()).toTypedArray())
        repository = OnboardingPrefsRepository(app).also { it.markComplete() }
    }
    @After fun cleanup() { store.clear() }
    private fun coordinator(bt: BluetoothStateManager, location: LocationStateManager) =
        OnboardingFlowCoordinator(permissions, bt, location, repository)

    private fun viewModel(f: ScannerFixture): MainViewModel {
        val bt = BluetoothStateManager(app, radio)
        val location = LocationStateManager(gps)
        return MainViewModel(coordinator(bt, location), bt, location, permissions, f.client).also { store.put("main", it) }
    }

    @Test fun B01_disabledBluetooth_mustNotBeDisplayedAsEnabled() = runTest(main.dispatcher) {
        radio.currentState = BluetoothState.DISABLED
        val vm = viewModel(ScannerFixture(backgroundScope, permissions))
        vm.refreshState()
        assertFalse("The UI flag must reflect the adapter, not a hard-coded true", vm.uiState.value.isBluetoothEnabled)
    }
    @Test fun B02_returningUserWithDisabledLocation_mustSeeEnableLocation() {
        gps.enabled = false
        val bt = BluetoothStateManager(app, radio)
        val flow = coordinator(bt, LocationStateManager(gps))
        flow.startAppFlow()
        assertEquals(OnboardingState.ENABLE_LOCATION, flow.currentState.value)
    }
    @Test fun B03_stop_mustCancelPendingCooldownStart() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope, permissions)
        f.client.startScanning()
        f.client.stopScanning()
        f.client.startScanning() // Deferred by the five-second cooldown.
        f.client.stopScanning() // The user cancelled the deferred start as well.
        advanceTimeBy(5000); runCurrent()
        verify(exactly = 1) { f.scanner.startScan(any(), any(), any<ScanCallback>()) }
    }
    @Test fun B04_radioFailures_mustReachScreenInsteadOfRemainingScanning() = runTest(main.dispatcher) {
        val observed = mutableListOf<BleScanStatus>()
        val codes = listOf(
            ScanCallback.SCAN_FAILED_ALREADY_STARTED,
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR,
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED,
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES,
            999
        )
        for (code in codes) {
            val f = ScannerFixture(backgroundScope, permissions)
            val vm = viewModel(f)
            vm.onStartScanClicked()
            f.callback.captured.onScanFailed(code)
            observed += vm.uiState.value.scanStatus
        }
        assertEquals("Error codes: $codes", List(codes.size) { BleScanStatus.ERROR }, observed)
    }
    @Test fun B05_unavailableScanner_mustNotReportSuccessfulScan() = runTest(main.dispatcher) {
        val vm = viewModel(ScannerFixture(backgroundScope, permissions, hasScanner = false))
        vm.onStartScanClicked()
        assertEquals(BleScanStatus.ERROR, vm.uiState.value.scanStatus)
    }
    @Test fun B06_nonConnectableAdvertisement_mustNotBeMarkedConnectable() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope, permissions)
        val received = mutableListOf<BleDeviceUiModel>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.client.discoveredDevices.toList(received) }
        f.client.startScanning()
        f.callback.captured.onScanResult(0, f.result(connectable = false))
        assertEquals(false, received.single().isConnectable)
    }
}
