package com.example.scratched.onboarding

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.scratched.constants.AppConstants
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.PermissionsManager
import com.example.scratched.onboarding.OnboardingStateRepository
import kotlinx.coroutines.delay

class AppFlowCoordinator(
    private val activity: ComponentActivity,
    private val permissionsManager: PermissionsManager,
    private val onboardingState: OnboardingStateRepository,
    private val onboardingComplete: () -> Unit,
    private val onboardingFailed: (String) -> Unit,
    private val updateOnboardingState: (OnboardingState) -> Unit
) {
    companion object {
        private val TAG = "AppFlowCoordinator"
    }

    private var isFirstTimeLaunch = true
    private var currentState : OnboardingState = OnboardingState.WELCOME
    private var permissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    // private var backgroundLocationLauncher: ActivityResultLauncher<String>? = null


    init {
        setupPermissionsLauncher()
        isFirstTimeLaunch = onboardingState.isFirstTimeLaunch()
    }

    private fun setupPermissionsLauncher() {
        permissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
                permissions -> handlePermissionResult(permissions)
        }
    }

    fun startAppFlow() {
        Log.d(TAG, "Starting App Flow")

        if (isFirstTimeLaunch) {
            startFirstTimeOnboarding()
            updateOnboardingState(currentState)
        } else {
            startSubsequentOnboarding()
        }
    }

    private fun startFirstTimeOnboarding() {
        currentState = OnboardingState.WELCOME
    }

    private fun startSubsequentOnboarding() {
        // Subsequent launch, check for missing required permissions
        val missingRequiredPermissions = permissionsManager.getMissingRequiredPermissions()
        Log.d(TAG, "Missing required permissions: $missingRequiredPermissions")

        if (missingRequiredPermissions.isEmpty()) {
            Log.d(TAG, "All required permissions are granted")
            currentState = OnboardingState.COMPLETED
            updateOnboardingState(currentState)
            return
        }

        currentState = determinateInitStateFromMissingPermissions(missingRequiredPermissions)
        Log.d(TAG, "Starting subsequent onboarding at state: $currentState")
        updateOnboardingState(currentState)
    }

    private fun determinateInitStateFromMissingPermissions(missingPermissions: List<String>): OnboardingState {
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
        val previousState = currentState
        currentState = when (currentState) {
            OnboardingState.WELCOME -> OnboardingState.ABOUT
            OnboardingState.ABOUT -> {
                if (permissionsManager.areAnyMissingPermissions(permissionsManager.getBluetoothPermissions())) {
                    OnboardingState.BLUETOOTH
                } else {
                    OnboardingState.ALL_SET
                }
            }
            OnboardingState.BLUETOOTH -> {
                if (permissionsManager.areAnyMissingPermissions(permissionsManager.getBluetoothPermissions())) {
                    Log.d(TAG, "User didn't grant Bluetooth permissions, required to grant manually from settings")
                    OnboardingState.BLUETOOTH
                }
                else if (permissionsManager.areAnyMissingPermissions(permissionsManager.getLocationPermissions())) {
                    OnboardingState.LOCATION
                } else {
                    OnboardingState.ALL_SET
                }
            }
            OnboardingState.LOCATION -> {
                if (permissionsManager.areAnyMissingPermissions(permissionsManager.getLocationPermissions())) {
                    Log.d(TAG, "User didn't grant Location permissions, required to grant manually from settings")
                    OnboardingState.LOCATION
                }
                if (shouldShowNotificationPermission()) {
                    OnboardingState.NOTIFICATION
                } else {
                    OnboardingState.ALL_SET
                }
            }
            OnboardingState.NOTIFICATION -> OnboardingState.ALL_SET
            OnboardingState.ALL_SET -> OnboardingState.COMPLETED
            OnboardingState.COMPLETED -> OnboardingState.COMPLETED
        }

        if (previousState != currentState) {
            Log.d(TAG, "Navigated from $previousState to $currentState")
            updateOnboardingState(currentState)
        }

        when (currentState) {
            OnboardingState.COMPLETED -> {
                // Only complete onboarding when we enter COMPLETED
                if (previousState != OnboardingState.COMPLETED) {
                    completeOnboarding()
                }
            }
            else -> {
                // should not happen
            }
        }
    }

    fun requestCurrentStatePermissions() {
        val permissions = when(currentState) {
            OnboardingState.BLUETOOTH -> {
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

        if (permissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions for $currentState: $permissions")
            permissionsLauncher?.launch(permissions.toTypedArray())
        } else {
            Log.d(TAG, "No permissions to request for $currentState - moving to the next step")
            navigateToNextStep()
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        Log.d(TAG, "Permissions result: $permissions")

        isFirstTimeLaunch = onboardingState.isFirstTimeLaunch()
        val allGranted = permissions.values.all { it }

        // Critical permissions are the ones required for BLE mesh to function.
        // Bluetooth and location are critical; notifications are optional.
        val deniedPermissions = permissions.filter { !it.value }.keys
        val areCriticalPermissions = deniedPermissions
            .intersect(permissionsManager.getRequiredPermissions().toSet())
            .isEmpty()  // true = no critical perms were denied

        when {
            areCriticalPermissions -> {
                if (!allGranted) {
                    // Some optional permissions were denied (e.g. notifications).
                    // That's fine — the app still works without them.
                    Log.d(TAG, "Optional permissions denied, can proceed")
                    navigateToNextStep()
                } else {
                    Log.d(TAG, "All permissions granted, moving to next step")
                    navigateToNextStep()
                }
            }
            else -> {
                // Critical permissions were denied. Behavior depends on whether
                // this is a first-time launch or a subsequent one.
                Log.d(TAG, "Critical permissions denied")
                handleCriticalPermissionsDenial(permissions)
            }
        }
    }

    private fun shouldShowNotificationPermission(): Boolean {
        return isFirstTimeLaunch &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                !permissionsManager.isPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handleCriticalPermissionsDenial(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys
        val criticalDenied = deniedPermissions
            .intersect(permissionsManager.getRequiredPermissions().toSet())

        if (deniedPermissions.isNotEmpty()) {
            val message = buildString {
                append("Critical permissions were denied. bitchat requires these permissions to function:\n")
                append("\nPlease grant these permissions in Settings to use bitchat.")
            }
            Log.w(TAG, "Critical permissions denied: $criticalDenied")
            onboardingFailed(message)
        }

    }

    private fun completeOnboarding() {
        Log.d(TAG, "Completing onboarding")

        if (AppConstants.DEBUG.ONBOARDING_RESET) {
            onboardingState.resetCompletion()
            return
        }

        onboardingState.markComplete()
        onboardingComplete()
    }
}