package com.example.scratched.integration

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.scratched.*
import com.example.scratched.onboarding.*
import com.example.scratched.support.*
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.utilities.stateManagers.*
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real scanner -> real ViewModel -> observable screen state, with simulated radio callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ScanningIntegrationTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private lateinit var app: Application
    private lateinit var permissions: PermissionsManager
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("scratch_onboarding", Context.MODE_PRIVATE).edit().clear().commit()
        permissions = PermissionsManager(app)
        shadowOf(app).grantPermissions(*permissions.getRequiredPermissions().toTypedArray())
    }
    @After fun cleanup() { store.clear() }

    private fun viewModel(f: ScannerFixture): MainViewModel {
        val bluetooth = BluetoothStateManager(app, FakeBluetoothAdapter())
        val location = LocationStateManager(FakeLocationAdapter())
        val repository = OnboardingPrefsRepository(app).also { it.markComplete() }
        val coordinator = OnboardingFlowCoordinator(permissions, bluetooth, location, repository)
        return MainViewModel(coordinator, bluetooth, location, permissions, f.client).also { store.put("main", it) }
    }
    @Test fun radioResults_updateOneScreenEntryPerAddress() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope, permissions)
        val vm = viewModel(f)
        vm.onStartScanClicked()
        f.callback.captured.onScanResult(0, f.result(name = null, rssi = -80))
        f.callback.captured.onScanResult(0, f.result(name = "PHOENIX", rssi = -45))
        f.callback.captured.onScanResult(0, f.result(address = "AA:BB:CC:DD:EE:02"))
        val state = vm.uiState.value
        assertEquals(BleScanStatus.SCANNING, state.scanStatus)
        assertEquals(2, state.discoveredDeviceCount)
        val first = state.devices.first { it.address.endsWith("01") }
        assertEquals(2, first.scanCount)
        assertEquals(-45, first.rssi)
        assertEquals("PHOENIX", first.displayName)
    }
    @Test fun stop_reachesRadioAndLeavesResultsVisible() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope, permissions)
        val vm = viewModel(f)
        vm.onStartScanClicked()
        f.callback.captured.onScanResult(0, f.result())
        vm.onStopScanClicked()
        verify(exactly = 1) { f.scanner.stopScan(f.callback.captured) }
        assertEquals(BleScanStatus.STOPPED, vm.uiState.value.scanStatus)
        assertEquals(1, vm.uiState.value.discoveredDeviceCount)
    }
    @Test fun deniedPermission_isRespectedAcrossRealPermissionAndScanComponents() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope, permissions)
        val vm = viewModel(f)
        shadowOf(app).denyPermissions(permissions.getBluetoothPermissions().first())
        vm.onStartScanClicked()
        assertEquals(BleScanStatus.PERMISSION_REQUIRED, vm.uiState.value.scanStatus)
        assertFalse(vm.uiState.value.hasRequiredPermissions)
        assertFalse(f.callback.isCaptured)
    }
    @Test fun nextScan_clearsPreviousSessionResults() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope, permissions)
        val vm = viewModel(f)
        vm.onStartScanClicked()
        f.callback.captured.onScanResult(0, f.result())
        vm.onStopScanClicked()
        advanceTimeBy(5000); runCurrent()
        vm.onStartScanClicked()
        assertTrue(vm.uiState.value.devices.isEmpty())
        assertEquals(0, vm.uiState.value.discoveredDeviceCount)
        f.callback.captured.onScanResult(0, f.result())
        assertEquals(1, vm.uiState.value.devices.single().scanCount)
    }
}
