package com.example.scratched


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.meshMVP.transport.BluetoothGattClientManager
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.utilities.stateManagers.BluetoothStateManager
import com.example.scratched.utilities.stateManagers.LocationStateManager
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingPrefsRepository
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.onboarding.OnboardingStatusRepository
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _activityEvents = MutableSharedFlow<ActivityEvent>()
    // accessible from Activity
    val activityEvents: SharedFlow<ActivityEvent> = _activityEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()
    private val deviceMap = mutableMapOf<String, BleDeviceUiModel>()

    init {
        onboardingFlowCoordinator.startAppFlow()
        bluetoothStatusManager.startMonitoring()
        updateBaseState()

        viewModelScope.launch {
            bluetoothStatusManager.bluetoothStateFlow.collect { state ->
                if (state == BluetoothState.DISABLED &&
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
                _activityEvents.emit(activityEvent)
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
                        name = if (existingDevice.name.isNullOrBlank()) newDevice.name else existingDevice.name
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
                _activityEvents.emit(
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
                _activityEvents.emit(ActivityEvent.EnableBluetooth(intent))
            } else {
                Log.e(TAG, "Cannot get EnableBluetooth event")
                _activityEvents.emit(ActivityEvent.ShowError("Unable to request EnableBluetooth"))
            }
        }
    }

    fun onEnableLocationClicked() {
        viewModelScope.launch {
            val intent = locationStateManager.getEnableLocationIntent()
            if (intent != null) {
                Log.d(TAG, "Emitting EnableLocation event")
                _activityEvents.emit(ActivityEvent.EnableLocation(intent))
            } else {
                Log.e(TAG, "Cannot get EnableLocation event")
                _activityEvents.emit(ActivityEvent.ShowError("Unable to request EnableLocation"))
            }
        }
    }

    fun onLocationSettingsClosed() {
        viewModelScope.launch {
            if (locationStateManager.isEnabled()) {
                onboardingFlowCoordinator.navigateToNextStep()
            } else {
                _activityEvents.emit(
                    ActivityEvent.ShowError("User declined/Error happened: Location is required ")
                )
            }
        }
    }

    private fun updateBaseState() {
        _uiState.update {
            it.copy(
                isBluetoothEnabled = true, // temp const val
                hasRequiredPermissions = permissionsManager.areAllRequiredPermissionsGranted()
            )
        }
    }

    fun onRestartOnboardingClick() {
        onboardingFlowCoordinator.resetOnboarding()
        onboardingFlowCoordinator.startAppFlow()
    }

    fun onStartScanClicked() {
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
                scanStatus = BleScanStatus.SCANNING,
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
    }
}
