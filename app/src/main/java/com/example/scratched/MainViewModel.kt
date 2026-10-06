package com.example.scratched

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch




class MainViewModel(
    //private val bluetoothStatusManager: AndroidBluetoothStatusManager,
    private val onboardingFlowCoordinator: OnboardingFlowCoordinator
) : ViewModel() {
    companion object {
        const val TAG = "MainViewModel"
    }

    val onboardingState: StateFlow<OnboardingState> = onboardingFlowCoordinator.currentState
    //val bluetoothState: StateFlow<BluetoothState> = bluetoothStatusManager.currentState

    private val _activityEvents = MutableSharedFlow<ActivityEvent>()
    val activityEvents: SharedFlow<ActivityEvent> = _activityEvents.asSharedFlow()

    init {
        onboardingFlowCoordinator.startAppFlow()

        viewModelScope.launch {
            onboardingFlowCoordinator.events.collect { event ->
                val activityEvent = when (event) {
                    is OnboardingEvent.RequestPermissions -> {
                        Log.d(TAG, "Requesting current permissions")
                        ActivityEvent.RequestPermissions(event.permissions)
                    }
                    is OnboardingEvent.ShowError -> {
                        ActivityEvent.ShowError(event.message)
                    }
                    is OnboardingEvent.RequestToOpenAppSettings -> {
                        ActivityEvent.ShowOpenAppScreen
                    }

                    is OnboardingEvent.RequestToEnableBluetooth -> TODO()
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

}

sealed interface ActivityEvent {
    data class RequestPermissions(val permissions: Array<String>) : ActivityEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RequestPermissions) return false
            return permissions.contentEquals(other.permissions)
        }
        override fun hashCode(): Int = permissions.contentHashCode()
    }
    object ShowOpenAppScreen: ActivityEvent
    data class EnableBluetooth(val intent: android.content.Intent) : ActivityEvent
    data class ShowError(val message: String) : ActivityEvent
}