package com.example.scratched


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.mesh.stateManagers.BluetoothStateManager
import com.example.scratched.mesh.stateManagers.LocationStateManager
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch


class MainViewModel(
    private val onboardingFlowCoordinator: OnboardingFlowCoordinator,
    private val bluetoothStatusManager: BluetoothStateManager,
    private val locationStateManager: LocationStateManager
) : ViewModel() {
    companion object {
        const val TAG = "MainViewModel"
    }

    // accessible  from Activity
    val onboardingState: StateFlow<OnboardingState> = onboardingFlowCoordinator.currentState
    //val bluetoothState: StateFlow<BluetoothState> = bluetoothStatusManager.currentState

    private val _activityEvents = MutableSharedFlow<ActivityEvent>()

    // accessible from Activity
    val activityEvents: SharedFlow<ActivityEvent> = _activityEvents.asSharedFlow()

    init {
        onboardingFlowCoordinator.startAppFlow()

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
}
