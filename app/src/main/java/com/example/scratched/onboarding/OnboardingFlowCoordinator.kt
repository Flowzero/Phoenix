package com.example.scratched.onboarding

import android.Manifest
import android.os.Build
import android.util.Log
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Central coordinator (State Machine) responsible for managing the application's onboarding flow
 *
 * OnboardingFlowCoordinator responsible for:
 * * Determining the correct onboarding screen to display based on the current permission state
 * * Marking the onboarding process as complete in persistent storage
 *
 * @property permissionsManager
 * @property onboardingStatusRepository
 */

class OnboardingFlowCoordinator(
    private val permissionsManager: PermissionsManager,
    private val onboardingStatusRepository: OnboardingStatusRepository,
) {
    companion object {
        private const val TAG = "OnboardingFlowCoordinator"
    }
    private var isFirstTimeLaunch: Boolean = onboardingStatusRepository.isFirstTimeLaunch()

    private val _currentState = MutableStateFlow<OnboardingState>(OnboardingState.WELCOME)
    // accessible from ViewModel
    val currentState: StateFlow<OnboardingState> = _currentState.asStateFlow()

    // one time Activity events (e.g. request permissions)
    private val _events = MutableSharedFlow<OnboardingEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    // accessible from ViewModel
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()


    fun startAppFlow() {
        Log.d(TAG, "Starting the onboarding flow: " +
                if (isFirstTimeLaunch) "First time launch" else "Subsequent launch"
        )

        if (isFirstTimeLaunch) {
            startFirstTimeOnboarding()
        } else {
            startSubsequentOnboarding()
        }
    }

    private fun startFirstTimeOnboarding() {
        _currentState.value = OnboardingState.WELCOME
    }

    private fun startSubsequentOnboarding() {
        val missingRequiredPermissions = permissionsManager.getMissingRequiredPermissions()
        Log.d(TAG, "Missing required permissions: $missingRequiredPermissions")

        if (missingRequiredPermissions.isEmpty()) {
            Log.d(TAG, "All required permissions are granted")
            completeOnboarding()
            return
        }

        _currentState.value = determineInitStateFromMissingPermissions(missingRequiredPermissions)
        Log.d(TAG, "Starting subsequent onboarding at state: ${_currentState.value}")
    }

    private fun determineInitStateFromMissingPermissions(missingPermissions: List<String>): OnboardingState {
        val bluetoothPermissions = permissionsManager.getBluetoothPermissions()
        val locationPermissions = permissionsManager.getLocationPermissions()

        val hasMissingBluetooth = missingPermissions.any { it in bluetoothPermissions }
        val hasMissingLocation = missingPermissions.any { it in locationPermissions }

        return when {
            hasMissingBluetooth -> OnboardingState.BLUETOOTH
            hasMissingLocation -> OnboardingState.LOCATION
            else -> OnboardingState.COMPLETED
        }
    }

    fun navigateToNextStep() {
        val previousState = _currentState.value
        Log.d(TAG, "navigateToNextStep called. Previous state: $previousState")

        val newState = when (previousState) {
            is OnboardingState.WELCOME -> OnboardingState.ABOUT
            is OnboardingState.ABOUT -> getNextMissingState()
            is OnboardingState.BLUETOOTH -> getNextMissingState()
            is OnboardingState.LOCATION -> getNextMissingState()
            is OnboardingState.NOTIFICATION -> OnboardingState.ALL_SET
            is OnboardingState.ALL_SET -> OnboardingState.COMPLETED
            is OnboardingState.COMPLETED -> OnboardingState.COMPLETED
            is OnboardingState.FAILED -> previousState // Stay on the error screen
            is OnboardingState.RUQUIRED_PERMISSION_REJECTED -> getNextMissingState()
        }

        _currentState.value = newState
        Log.d(TAG, "New state evaluated: $newState")

        if (previousState != currentState) {
            Log.d(TAG, "SUCCESS: Navigated from $previousState to $newState")
        } else {
            Log.w(TAG, "WARNING: State did not change! Stuck at $newState")
        }

        if (newState == OnboardingState.COMPLETED && previousState != OnboardingState.COMPLETED) {
            completeOnboarding()
        }
    }

    private fun getNextMissingState(): OnboardingState {
        val missingBluetooth = permissionsManager.getBluetoothPermissions()
            .any { !permissionsManager.isPermissionGranted(it) }

        val missingLocation = permissionsManager.getLocationPermissions()
            .any { !permissionsManager.isPermissionGranted(it) }

        val missingNotification = shouldShowNotificationPermission()

        Log.d(TAG, "Checking missing permissions +" +
                "\n\t-> BT: $missingBluetooth," +
                "\n\t->Loc: $missingLocation," +
                "\n\t->Notif: $missingNotification")

        return when {
            missingBluetooth -> OnboardingState.BLUETOOTH
            missingLocation -> OnboardingState.LOCATION
            missingNotification -> OnboardingState.NOTIFICATION
            else -> OnboardingState.ALL_SET
        }
    }

    fun requestCurrentStatePermissions() {
        Log.d(TAG, "requestCurrentStatePermissions called for state: ${_currentState.value}")

        val permissionsToRequest = when (val state = _currentState.value) {
            is OnboardingState.BLUETOOTH -> {
                permissionsManager.getBluetoothPermissions()
                    .filter { !permissionsManager.isPermissionGranted(it) }
            }
            OnboardingState.LOCATION -> {
                permissionsManager.getLocationPermissions()
                    .filter { !permissionsManager.isPermissionGranted(it) }
            }
            OnboardingState.NOTIFICATION -> {
                permissionsManager.getNotificationPermissions()
                    .filter { !permissionsManager.isPermissionGranted(it) }
            }
            else -> emptyList()
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsToRequest")
            _events.tryEmit(OnboardingEvent.RequestPermissions(permissionsToRequest.toTypedArray()))
        } else {
            Log.d(TAG, "No permissions to request for ${_currentState.value} - moving to the next step")
            navigateToNextStep()
        }
    }

    fun onHandlePermissionsResult(permissions: Map<String, Boolean>) {
        Log.d(TAG, "Permissions result: $permissions")

        val deniedPermissions = permissions.filter { !it.value }.keys
        val requiredPermissions = permissionsManager.getRequiredPermissions().toSet()
        val deniedCriticalPermissions = deniedPermissions.intersect(requiredPermissions)

        when {
            deniedCriticalPermissions.isEmpty() -> {
                Log.d(TAG, "Critical permissions granted (or only optional denied), moving to next step")
                navigateToNextStep()
            }
            else -> {
                Log.w(TAG, "Critical permissions denied: $deniedCriticalPermissions")
                _currentState.value = OnboardingState.RUQUIRED_PERMISSION_REJECTED
                onOpenSettingsRequested()
            }
        }
    }

    private fun shouldShowNotificationPermission(): Boolean {
        return isFirstTimeLaunch &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !permissionsManager.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun onOpenSettingsRequested() {
        _events.tryEmit(OnboardingEvent.RequestToOpenAppSettings)
    }

    private fun completeOnboarding() {
        onboardingStatusRepository.markComplete()
        _currentState.value = OnboardingState.COMPLETED

        Log.d(TAG, "Onboarding completed, navigating to main app screen")
    }
}