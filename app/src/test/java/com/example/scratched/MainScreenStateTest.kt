package com.example.scratched

import android.content.Intent
import androidx.lifecycle.ViewModelStore
import com.example.scratched.meshMVP.transport.BluetoothGattClientManager
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.support.*
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.utilities.stateManagers.BluetoothStateManager
import com.example.scratched.utilities.stateManagers.LocationStateManager
import io.mockk.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenStateTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val logs = AndroidLogRule()
    private val store = ViewModelStore()
    private val state = MutableStateFlow<OnboardingState>(OnboardingState.WELCOME)
    private val bluetoothState = MutableStateFlow<BluetoothState>(BluetoothState.ENABLED)
    private val discoveries = MutableSharedFlow<BleDeviceUiModel>()
    private val coordinator = mockk<OnboardingFlowCoordinator>(relaxed = true)
    private val bluetooth = mockk<BluetoothStateManager>(relaxed = true)
    private val location = mockk<LocationStateManager>()
    private val permissions = mockk<PermissionsManager>()
    private val scanner = mockk<BluetoothGattClientManager>(relaxed = true)
    private lateinit var vm: MainViewModel

    @Before fun setup() {
        every { coordinator.currentState } returns state
        every { coordinator.events } returns MutableSharedFlow<OnboardingEvent>()
        every { bluetooth.bluetoothStateFlow } returns bluetoothState
        every { permissions.areAllRequiredPermissionsGranted() } returns true
        every { scanner.discoveredDevices } returns discoveries
        vm = MainViewModel(coordinator, bluetooth, location, permissions, scanner)
        store.put("test", vm)
    }
    @After fun cleanup() { store.clear() }

    @Test fun initialization_startsBluetoothMonitoring() {
        verify(exactly = 1) { bluetooth.startMonitoring() }
    }
    @Test fun clearingViewModel_stopsBluetoothMonitoring() {
        store.clear()
        verify(exactly = 1) { bluetooth.stopMonitoring() }
    }
    @Test fun refresh_updatesPermissionFlag() {
        every { permissions.areAllRequiredPermissionsGranted() } returns false
        vm.refreshState()
        assertFalse(vm.uiState.value.hasRequiredPermissions)
        every { permissions.areAllRequiredPermissionsGranted() } returns true
        vm.refreshState()
        assertTrue(vm.uiState.value.hasRequiredPermissions)
    }
    @Test fun start_withoutPermissions_doesNotCallRadio() {
        every { permissions.areAllRequiredPermissionsGranted() } returns false
        vm.onStartScanClicked()
        assertEquals(BleScanStatus.PERMISSION_REQUIRED, vm.uiState.value.scanStatus)
        assertNull(vm.uiState.value.scanStartedAtMillis)
        verify(exactly = 0) { scanner.startScanning() }
    }
    @Test fun start_marksScanAndCallsManager() {
        vm.onStartScanClicked()
        assertEquals(BleScanStatus.SCANNING, vm.uiState.value.scanStatus)
        assertNotNull(vm.uiState.value.scanStartedAtMillis)
        verify(exactly = 1) { scanner.startScanning() }
    }
    @Test fun stop_stopsRadioAndUpdatesScreen() {
        vm.onStartScanClicked()
        vm.onStopScanClicked()
        assertEquals(BleScanStatus.STOPPED, vm.uiState.value.scanStatus)
        verify(exactly = 1) { scanner.stopScanning() }
    }
    @Test fun discovery_addsDeviceAndCount() = runTest(main.dispatcher) {
        val found = device()
        discoveries.emit(found)
        assertEquals(listOf(found), vm.uiState.value.devices)
        assertEquals(1, vm.uiState.value.discoveredDeviceCount)
    }
    @Test fun repeatedDiscovery_updatesExistingDeviceWithoutDuplicate() = runTest(main.dispatcher) {
        discoveries.emit(device(firstSeen = 100, lastSeen = 100))
        discoveries.emit(device(rssi = -42, firstSeen = 200, lastSeen = 200))
        val screen = vm.uiState.value
        assertEquals(1, screen.discoveredDeviceCount)
        assertEquals(1, screen.devices.size)
        with(screen.devices.single()) {
            assertEquals(-42, rssi)
            assertEquals(100L, firstSeenAtMillis)
            assertEquals(200L, lastSeenAtMillis)
            assertEquals(2, scanCount)
        }
    }
    @Test fun discoveries_withDifferentAddressesRemainSeparate() = runTest(main.dispatcher) {
        discoveries.emit(device(address = "one"))
        discoveries.emit(device(address = "two"))
        assertEquals(setOf("one", "two"), vm.uiState.value.devices.map { it.address }.toSet())
        assertEquals(2, vm.uiState.value.discoveredDeviceCount)
    }
    @Test fun missingName_isFilledByLaterDiscovery() = runTest(main.dispatcher) {
        discoveries.emit(device(name = null))
        discoveries.emit(device(name = "PHOENIX"))
        assertEquals("PHOENIX", vm.uiState.value.devices.single().displayName)
    }
    @Test fun knownName_isNotLostOnUnnamedAdvertisement() = runTest(main.dispatcher) {
        discoveries.emit(device(name = "PHOENIX"))
        discoveries.emit(device(name = null))
        assertEquals("PHOENIX", vm.uiState.value.devices.single().displayName)
    }
    @Test fun newScan_clearsPreviousResultsAndCounter() = runTest(main.dispatcher) {
        discoveries.emit(device())
        vm.onStartScanClicked()
        assertTrue(vm.uiState.value.devices.isEmpty())
        assertEquals(0, vm.uiState.value.discoveredDeviceCount)
        discoveries.emit(device())
        assertEquals(1, vm.uiState.value.devices.single().scanCount)
    }
    @Test fun bluetoothOff_afterCompletion_rechecksOnboarding() {
        state.value = OnboardingState.COMPLETED
        bluetoothState.value = BluetoothState.DISABLED
        verify(exactly = 1) { coordinator.navigateToNextStep() }
    }
    @Test fun bluetoothOff_duringOnboarding_doesNotSkipStep() {
        bluetoothState.value = BluetoothState.DISABLED
        verify(exactly = 0) { coordinator.navigateToNextStep() }
    }
    @Test fun bluetoothEnabled_doesNotNavigate() {
        state.value = OnboardingState.COMPLETED
        bluetoothState.value = BluetoothState.NOT_SUPPORTED
        bluetoothState.value = BluetoothState.ENABLED
        verify(exactly = 0) { coordinator.navigateToNextStep() }
    }
    @Test fun restart_resetsAndStartsOnboarding() {
        vm.onRestartOnboardingClick()
        verifyOrder { coordinator.resetOnboarding(); coordinator.startAppFlow() }
    }
    @Test fun enableLocation_emitsSettingsIntent() = runTest(main.dispatcher) {
        val intent = mockk<Intent>()
        every { location.getEnableLocationIntent() } returns intent
        val event = async(start = CoroutineStart.UNDISPATCHED) { vm.activityEvents.first() }
        vm.onEnableLocationClicked()
        assertEquals(ActivityEvent.EnableLocation(intent), event.await())
    }
    @Test fun enableLocation_withoutIntent_emitsError() = runTest(main.dispatcher) {
        every { location.getEnableLocationIntent() } returns null
        val event = async(start = CoroutineStart.UNDISPATCHED) { vm.activityEvents.first() }
        vm.onEnableLocationClicked()
        assertEquals(ActivityEvent.ShowError("Unable to request EnableLocation"), event.await())
    }
    @Test fun returningFromLocationSettings_enabled_continuesFlow() {
        every { location.isEnabled() } returns true
        vm.onLocationSettingsClosed()
        verify(exactly = 1) { coordinator.navigateToNextStep() }
    }
    @Test fun returningFromLocationSettings_disabled_emitsErrorAndStays() = runTest(main.dispatcher) {
        every { location.isEnabled() } returns false
        val event = async(start = CoroutineStart.UNDISPATCHED) { vm.activityEvents.first() }
        vm.onLocationSettingsClosed()
        assertEquals(ActivityEvent.ShowError("User declined/Error happened: Location is required "), event.await())
        verify(exactly = 0) { coordinator.navigateToNextStep() }
    }
}
