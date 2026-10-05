package com.example.scratched

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.UiEvent.*
import com.example.scratched.mesh.BluetoothStateManager
import com.example.scratched.mesh.BluetoothState
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    init {
        onboardingFlowCoordinator.startAppFlow()

        viewModelScope.launch {
            onboardingFlowCoordinator.events.collect { event ->
                val uiEvent = when (event) {
                    is OnboardingEvent.RequestPermissions -> {
                        Log.d(TAG, "Requesting current permissions")
                        RequestPermissions(event.permissions)
                    }
                    is OnboardingEvent.NavigateToMainApp ->
                        UiEvent.NavigateToMainApp
                    is OnboardingEvent.ShowError ->
                        ShowError(event.message)
                    is OnboardingEvent.ShowOpenAppScreen -> {
                        UiEvent.ShowOpenAppScreen
                    }

                    is OnboardingEvent.EnableBluetooth -> TODO()
                }
                _uiEvents.emit(uiEvent)
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

    /*
    fun onEnableBluetoothClicked() {
        viewModelScope.launch {
            bluetoothStatusManager.getEnableBluetoothIntent()?.let { intent ->
                _uiEvents.emit(UiEvent.EnableBluetooth(intent))
            }
        }
    }
     */

    fun onOpenSettingsClicked() {
        onboardingFlowCoordinator.onOpenSettingsRequested()
    }

}

sealed interface UiEvent {
    data class RequestPermissions(val permissions: Array<String>) : UiEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RequestPermissions) return false
            return permissions.contentEquals(other.permissions)
        }
        override fun hashCode(): Int = permissions.contentHashCode()
    }
    object ShowOpenAppScreen: UiEvent
    data class EnableBluetooth(val intent: android.content.Intent) : UiEvent
    data class ShowError(val message: String) : UiEvent
    object NavigateToMainApp : UiEvent
}