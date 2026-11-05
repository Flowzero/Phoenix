package com.example.scratched


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.meshMVP.transport.BleDiscovery
import com.example.scratched.meshMVP.transport.DiscoveryFilter
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

    private val permissionsManager: PermissionsManager,
    private val discovery: BleDiscovery
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

    init {
        onboardingFlowCoordinator.startAppFlow()
        bluetoothStatusManager.startMonitoring()
        updateBaseState()

        viewModelScope.launch {
            bluetoothStatusManager.bluetoothStateFlow.collect { state ->
                updateBaseState()
                if (state != BluetoothState.ENABLED) discovery.refreshReadiness()
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
            discovery.state.collect { snapshot ->
                _uiState.update { snapshot.toUiState(it) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { discovery.close() } finally { bluetoothStatusManager.stopMonitoring() }
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
        refreshState()
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
                hasRequiredPermissions = permissionsManager.hasScanPermissions()
            )
        }
    }

    fun onRestartOnboardingClick() {
        onStopScanClicked()
        onboardingFlowCoordinator.resetOnboarding()
        onboardingFlowCoordinator.startAppFlow()
    }

    fun onStartScanClicked() {
        updateBaseState()
        discovery.startScanning(if (_uiState.value.showAllDevices) DiscoveryFilter.ALL_DEVICES else DiscoveryFilter.PHOENIX_ONLY)
    }

    fun onStopScanClicked() = discovery.stopScanning()

    fun onShowAllDevicesChanged(showAll: Boolean) {
        if (!discovery.state.value.scan.phase.isActive) {
            _uiState.update { it.copy(showAllDevices = showAll) }
        }
    }

    fun onDeviceClick(device: BleDeviceUiModel) {
        Log.d(TAG, "Device clicked: ${device.displayName} (${device.address})")
        // Discovery only: no GATT connection is implemented yet.
    }

    fun refreshState() {
        updateBaseState()
        discovery.refreshReadiness()
        onboardingFlowCoordinator.refreshRequirements()
    }
}
