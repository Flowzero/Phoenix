package com.example.scratched

import com.example.scratched.user.UserProfile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.transport.meshBLE.BleScanner
import com.example.scratched.transport.meshBLE.BleAdvertiser
import com.example.scratched.meshMVP.transport.chat.DirectChat
import com.example.scratched.transport.meshBLE.AdvertisingPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.utilities.stateManagers.BluetoothStateManager
import com.example.scratched.utilities.stateManagers.LocationStateManager
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(
    private val onboardingFlowCoordinator: OnboardingFlowCoordinator,
    private val bluetoothStatusManager: BluetoothStateManager,
    private val locationStateManager: LocationStateManager,

    private val permissionsManager: PermissionsManager,
    private val gattClientManager: BleScanner,
    val chat: DirectChat,
    private val gattServerManager: BleAdvertiser
) : ViewModel() {
    companion object {
        const val TAG = "MainViewModel"
    }

    // accessible  from Activity
    val onboardingState: StateFlow<OnboardingState> = onboardingFlowCoordinator.currentState

    private val _activityEvents = MutableSharedFlow<ActivityEvent>()
    // accessible from Activity
    val activityEvents: SharedFlow<ActivityEvent> = _activityEvents.asSharedFlow()

    private val directory = NearbyPeople(gattServerManager.profile.id)
    private val _people = MutableStateFlow(emptyList<NearbyPerson>())
    val people = _people.asStateFlow()
    private val _profile = MutableStateFlow(gattServerManager.profile)
    val profile = _profile.asStateFlow()
    val scanError = gattClientManager.scanError
    private var foreground = false
    private var discovery: Job? = null

    init {
        onboardingFlowCoordinator.startAppFlow()
        bluetoothStatusManager.startMonitoring()
        reconcileDiscovery()

        viewModelScope.launch {
            bluetoothStatusManager.bluetoothStateFlow.collect { state ->
                reconcileDiscovery()
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
            gattServerManager.advertisingState.collect { state ->
                if (state.phase == AdvertisingPhase.ERROR) {
                    chat.advertisingFailed(state.error ?: "Не удалось запустить рекламу Bluetooth")
                }
            }
        }
        viewModelScope.launch { onboardingState.collect { reconcileDiscovery() } }
        viewModelScope.launch {
            gattClientManager.discoveredDevices.collect { result ->
                if (discovery == null) return@collect
                directory.observe(result.profile, result.address, result.observedAtMillis)
                refreshPeople()
            }
        }
    }

    private fun refreshPeople() {
        _people.value = directory.present(android.os.SystemClock.elapsedRealtime())
    }

    fun setForeground(visible: Boolean) {
        foreground = visible
        if (visible && onboardingState.value == OnboardingState.COMPLETED &&
            (!permissionsManager.areAllRequiredPermissionsGranted() || !bluetoothStatusManager.isEnabled())) {
            onboardingFlowCoordinator.navigateToNextStep()
        }
        reconcileDiscovery()
    }

    private fun reconcileDiscovery() {
        val enabled = foreground && onboardingState.value == OnboardingState.COMPLETED &&
            bluetoothStatusManager.isEnabled() && permissionsManager.areAllRequiredPermissionsGranted()
        if (!enabled) {
            discovery?.cancel()
            discovery = null
            gattClientManager.stopScanning()
            chat.close()
            directory.clear()
            refreshPeople()
            return
        }
        if (discovery != null) return
        discovery = viewModelScope.launch {
            while (true) {
                refreshPeople()
                delay(3_000)
            }
        }
        gattClientManager.startScanning()
        chat.host()
    }

    fun openConversation(person: NearbyPerson) {
        val current = directory.present(android.os.SystemClock.elapsedRealtime()).find { it.profile.id == person.profile.id }
        if (discovery != null && current != null && chat.state.value.peer == null) {
            chat.connect(current.address, current.profile)
        }
    }

    fun returnToPeople() {
        if (discovery != null) chat.host() else chat.close()
    }

    fun retryDiscovery() {
        if (discovery == null) return
        gattClientManager.startScanning()
        if (chat.state.value.peer == null) chat.host()
    }

    fun rename(name: String) {
        if (UserProfile.validName(name) && name != profile.value.name) {
            gattServerManager.rename(name)
            _profile.value = gattServerManager.profile
            if (discovery != null && chat.state.value.peer == null) chat.host()
        }
    }

    override fun onCleared() {
        super.onCleared()
        chat.close()
        gattServerManager.close()
        gattClientManager.stopScanning()
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
        reconcileDiscovery()
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

}
