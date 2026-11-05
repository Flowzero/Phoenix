package com.example.scratched


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.meshMVP.transport.BluetoothGattClientManager
import com.example.scratched.meshMVP.transport.ScanPhase
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.utilities.stateManagers.BluetoothStateManager
import com.example.scratched.utilities.stateManagers.LocationStateManager
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(
    private val onboardingFlowCoordinator: OnboardingFlowCoordinator,
    private val bluetoothStatusManager: BluetoothStateManager,
    private val locationStateManager: LocationStateManager,

    //TEST
    private val permissionsManager: PermissionsManager,
    private val gattClientManager: BluetoothGattClientManager
) : ViewModel() {
    companion object {
        const val TAG = "MainViewModel"
    }

    // accessible  from Activity
    val onboardingState: StateFlow<OnboardingState> = onboardingFlowCoordinator.currentState

    private val _activityEvents = Channel<ActivityEvent>(Channel.BUFFERED)
    // accessible from Activity
    val activityEvents = _activityEvents.receiveAsFlow()

    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()
    private val deviceMap = mutableMapOf<String, BleDeviceUiModel>()

    init {
        onboardingFlowCoordinator.startAppFlow()
        bluetoothStatusManager.startMonitoring()
        updateBaseState()

        viewModelScope.launch {
            bluetoothStatusManager.bluetoothStateFlow.collect { state ->
                updateBaseState()
                if (state != BluetoothState.ENABLED) gattClientManager.stopScanning()
                if (state != BluetoothState.ENABLED &&
                    onboardingState.value == OnboardingState.COMPLETED) {

                    onboardingFlowCoordinator.navigateToNextStep()
                }
            }
        }

        viewModelScope.launch {
            onboardingFlowCoordinator.events.collect { event ->
                val activityEvent = when (event) {
                    is OnboardingEvent.RequestToRequestPermissions -> {
                        Log.d(TAG, "Requesting current permissions")
                        ActivityEvent.RequestPermissions(event.permissions)
                    }
                    is OnboardingEvent.RequestToShowError -> {
                        ActivityEvent.ShowError(event.message)
                    }
                }
                _activityEvents.send(activityEvent)
            }
        }

        viewModelScope.launch {
            gattClientManager.scanState.collect { state ->
                val status = when (state.phase) {
                    ScanPhase.IDLE -> BleScanStatus.IDLE
                    ScanPhase.WAITING -> BleScanStatus.WAITING
                    ScanPhase.SCANNING -> BleScanStatus.SCANNING
                    ScanPhase.STOPPED -> BleScanStatus.STOPPED
                    ScanPhase.PERMISSION_REQUIRED -> BleScanStatus.PERMISSION_REQUIRED
                    ScanPhase.BLUETOOTH_DISABLED -> BleScanStatus.BLUETOOTH_DISABLED
                    ScanPhase.ERROR -> BleScanStatus.ERROR
                }
                _uiState.update { it.copy(scanStatus = status, errorMessage = state.error) }
            }
        }

        viewModelScope.launch {
            gattClientManager.discoveredDevices.collect { newDevice ->
                val existingDevice = deviceMap[newDevice.address]

                val updatedDevice = if (existingDevice != null) {
                    existingDevice.copy(
                        rssi = newDevice.rssi,
                        lastSeenAtMillis = newDevice.lastSeenAtMillis,
                        scanCount = existingDevice.scanCount + 1,
                        name = newDevice.name ?: existingDevice.name,
                        txPower = newDevice.txPower,
                        serviceUuids = newDevice.serviceUuids,
                        manufacturerData = newDevice.manufacturerData,
                        isConnectable = newDevice.isConnectable
                    )
                } else {
                    newDevice
                }

                deviceMap[updatedDevice.address] = updatedDevice

                _uiState.update { currentState ->
                    currentState.copy(
                        devices = deviceMap.values.toList(),
                        discoveredDeviceCount = deviceMap.size
                    )
                }
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        gattClientManager.close()
        bluetoothStatusManager.stopMonitoring()
    }

    // UI commands

    fun onNextClicked() {
        onboardingFlowCoordinator.navigateToNextStep()
    }

    fun onRequestPermissionsClicked() {
        onboardingFlowCoordinator.requestCurrentStatePermissions()
    }

    fun onPermissionsResult(result: Map<String, Boolean>) {
        onboardingFlowCoordinator.onHandlePermissionsResult(result)
    }

    fun onBluetoothEnableResult(isSuccess: Boolean) {
        if (!isSuccess) {
            viewModelScope.launch {
                _activityEvents.send(
                    ActivityEvent.ShowError("User declined/Error happened: Bluetooth is required")
                )
            }
        } else {
            onboardingFlowCoordinator.navigateToNextStep()
        }
    }

    fun onEnableBluetoothClicked() {
        viewModelScope.launch {
            val intent = bluetoothStatusManager.getEnableBluetoothIntent()
            if (intent != null) {
                Log.d(TAG, "Emitting EnableBluetooth event")
                _activityEvents.send(ActivityEvent.EnableBluetooth(intent))
            } else {
                Log.e(TAG, "Cannot get EnableBluetooth event")
                _activityEvents.send(ActivityEvent.ShowError("Unable to request EnableBluetooth"))
            }
        }
    }

    fun onEnableLocationClicked() {
        viewModelScope.launch {
            val intent = locationStateManager.getEnableLocationIntent()
            if (intent != null) {
                Log.d(TAG, "Emitting EnableLocation event")
                _activityEvents.send(ActivityEvent.EnableLocation(intent))
            } else {
                Log.e(TAG, "Cannot get EnableLocation event")
                _activityEvents.send(ActivityEvent.ShowError("Unable to request EnableLocation"))
            }
        }
    }

    fun onLocationSettingsClosed() {
        viewModelScope.launch {
            if (locationStateManager.isEnabled()) {
                onboardingFlowCoordinator.navigateToNextStep()
            } else {
                _activityEvents.send(
                    ActivityEvent.ShowError("User declined/Error happened: Location is required ")
                )
            }
        }
    }

    private fun updateBaseState() {
        _uiState.update {
            it.copy(
                isBluetoothEnabled = bluetoothStatusManager.isEnabled(),
                hasRequiredPermissions = permissionsManager.areAllRequiredPermissionsGranted()
            )
        }
    }

    fun onRestartOnboardingClick() {
        onStopScanClicked()
        onboardingFlowCoordinator.resetOnboarding()
        onboardingFlowCoordinator.startAppFlow()
    }

    fun onStartScanClicked() {
        if (gattClientManager.scanState.value.phase in listOf(ScanPhase.SCANNING, ScanPhase.WAITING)) return
        updateBaseState()
        val currentState = _uiState.value

        if (!currentState.hasRequiredPermissions) {
            _uiState.update { it.copy(scanStatus = BleScanStatus.PERMISSION_REQUIRED) }
            return
        }
        if (!currentState.isBluetoothEnabled) {
            _uiState.update { it.copy(scanStatus = BleScanStatus.BLUETOOTH_DISABLED) }
            return
        }

        deviceMap.clear()
        _uiState.update {
            it.copy(
                scanStartedAtMillis = System.currentTimeMillis(),
                devices = emptyList(),
                discoveredDeviceCount = 0
            )
        }

        gattClientManager.startScanning()
    }

    fun onStopScanClicked() {
        gattClientManager.stopScanning()
        _uiState.update { it.copy(scanStatus = BleScanStatus.STOPPED) }
    }

    fun onDeviceClick(device: BleDeviceUiModel) {
        Log.d(TAG, "Device clicked: ${device.displayName} (${device.address})")
        // TODO: call gattClientManager.connectToDevice(device.address)
    }

    fun refreshState() {
        updateBaseState()
        if (!permissionsManager.areAllRequiredPermissionsGranted() || !bluetoothStatusManager.isEnabled() ||
            (permissionsManager.requiresLocationServices() && !locationStateManager.isEnabled())) {
            gattClientManager.stopScanning()
        }
        onboardingFlowCoordinator.refreshRequirements()
    }
}
