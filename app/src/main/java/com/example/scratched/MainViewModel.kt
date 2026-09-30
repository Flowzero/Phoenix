package com.example.scratched

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.mesh.AndroidBluetoothStatusManager
import com.example.scratched.mesh.BluetoothState
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val bluetoothStatusManager: AndroidBluetoothStatusManager,
    private val onboardingFlowCoordinator: OnboardingFlowCoordinator
) : ViewModel() {
    /*private val _onboardingState = MutableStateFlow<OnboardingState>(OnboardingState.WELCOME)
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    private val _bluetoothState = MutableStateFlow<BluetoothState>(BluetoothState.DISABLED)
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    fun updateOnboardingState(state: OnboardingState) {
        viewModelScope.launch {
            _onboardingState.value = state
        }
    }

    fun updateBluetoothState(state: BluetoothState) {
        viewModelScope.launch {
            _bluetoothState.value = state
        }
    }
    */

    val onboardingState: StateFlow<OnboardingState> = onboardingFlowCoordinator.currentState
    //val bluetoothState: StateFlow<BluetoothState> = bluetoothStatusManager.currentState

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            onboardingFlowCoordinator.events.collect { event ->
                val uiEvent = when (event) {
                    is OnboardingEvent.RequestPermissions ->
                        UiEvent.RequestPermissions(event.permissions)
                    is OnboardingEvent.NavigateToMainApp ->
                        UiEvent.NavigateToMainApp
                    is OnboardingEvent.ShowError ->
                        UiEvent.ShowError(event.message)
                }
                _uiEvents.emit(uiEvent)
            }
        }

    }

    // ==================== Команды от UI ====================

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
    data class EnableBluetooth(val intent: android.content.Intent) : UiEvent
    data class ShowError(val message: String) : UiEvent
    object NavigateToMainApp : UiEvent
}