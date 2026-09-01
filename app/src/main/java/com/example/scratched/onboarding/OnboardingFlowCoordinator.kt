package com.example.scratched.onboarding

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.constants.AppConstants
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Central coordinator (State Machine) responsible for managing the application's
 * onboarding flow and runtime permissions requests.
 *
 * RESPONSIBILITIES:
 * - Determining the correct onboarding screen to display based on the current permission state
 * - Orchestrating the request for runtime permissions via Android's ActivityResult API.
 * - Handling the results of permission requests and routing the user to the next logical step.
 * - Marking the onboarding process as complete in persistent storage.
 *
 * ARCHITECTURAL ROLE:
 * This class acts as the "brain" of the onboarding process. It is completely decoupled
 * from the UI layer (Jetpack Compose). Instead of directly manipulating UI components,
 * it communicates with the UI via callback functions (e.g., [updateOnboardingState]),
 * allowing the ViewModel/Activity to react to state changes.
 */

class OnboardingFlowCoordinator(
    /**
     * The host Activity, required to register and launch the system permission dialog
     * via [ActivityResultLauncher].
     */
    private val activity: ComponentActivity,

    /**
     * Utility class responsible for checking permission grant states and providing
     * OS-version-specific lists of required permissions.
     */
    private val permissionsManager: PermissionsManager,

    /**
     * Repository interface for persisting the onboarding completion state (e.g., via SharedPreferences).
     */
    private val onboardingState: OnboardingStatusRepository,

    /**
     * Callback triggered when the entire onboarding flow is successfully completed.
     * The UI layer uses this to navigate to the main application screen.
     */
    private val onboardingComplete: () -> Unit,

    /**
     * Callback triggered when the user permanently denies critical permissions.
     * Provides an error message that the UI can display to guide the user to Settings.
     */
    private val onboardingFailed: (String) -> Unit,

    /**
     * Callback used to push state changes to the UI (typically via a ViewModel's StateFlow).
     * This triggers Jetpack Compose to recompose and show the correct screen.
     */
    private val updateOnboardingState: (OnboardingState) -> Unit
) {
    companion object {
        private const val TAG = "OnboardingFlowCoordinator"
    }
    // ...

    private var isFirstTimeLaunch = true
    private var currentState: OnboardingState = OnboardingState.WELCOME
    private var permissionsLauncher: ActivityResultLauncher<Array<String>>? = null

    init {
        setupPermissionsLauncher()
        isFirstTimeLaunch = onboardingState.isFirstTimeLaunch()
    }

    private fun setupPermissionsLauncher() {
        permissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResult(permissions)
        }
    }

    fun startAppFlow() {
        Log.d(TAG, "Starting App Flow. isFirstTimeLaunch=$isFirstTimeLaunch")
        if (isFirstTimeLaunch) {
            startFirstTimeOnboarding()
        } else {
            startSubsequentOnboarding()
        }
        updateOnboardingState(currentState)
    }

    private fun startFirstTimeOnboarding() {
        currentState = OnboardingState.WELCOME
    }

    private fun startSubsequentOnboarding() {
        val missingRequiredPermissions = permissionsManager.getMissingRequiredPermissions()
        Log.d(TAG, "Missing required permissions: $missingRequiredPermissions")

        if (missingRequiredPermissions.isEmpty()) {
            Log.d(TAG, "All required permissions are granted")
            currentState = OnboardingState.COMPLETED
            return
        }

        currentState = determineInitStateFromMissingPermissions(missingRequiredPermissions)
        Log.d(TAG, "Starting subsequent onboarding at state: $currentState")
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
        val previousState = currentState
        Log.d(TAG, "navigateToNextStep called. Previous state: $previousState")

        currentState = when (previousState) {
            OnboardingState.WELCOME -> OnboardingState.ABOUT
            OnboardingState.ABOUT -> getNextMissingState()
            OnboardingState.BLUETOOTH -> getNextMissingState()
            OnboardingState.LOCATION -> getNextMissingState()
            OnboardingState.NOTIFICATION -> OnboardingState.ALL_SET
            OnboardingState.ALL_SET -> OnboardingState.COMPLETED
            OnboardingState.COMPLETED -> OnboardingState.COMPLETED
        }

        Log.d(TAG, "New state evaluated: $currentState")

        if (previousState != currentState) {
            Log.d(TAG, "SUCCESS: Navigated from $previousState to $currentState")
            updateOnboardingState(currentState)
        } else {
            Log.w(TAG, "WARNING: State did not change! Stuck at $currentState")
        }

        if (currentState == OnboardingState.COMPLETED && previousState != OnboardingState.COMPLETED) {
            completeOnboarding()
        }
    }

    /**
     * Evaluates permissions in priority order to ensure no screens are skipped.
     */
    private fun getNextMissingState(): OnboardingState {
        val missingBluetooth = permissionsManager.getBluetoothPermissions()
            .any { !permissionsManager.isPermissionGranted(it) }

        val missingLocation = permissionsManager.getLocationPermissions()
            .any { !permissionsManager.isPermissionGranted(it) }

        val missingNotification = shouldShowNotificationPermission()

        Log.d(TAG, "Checking missing permissions -> BT: $missingBluetooth, Loc: $missingLocation, Notif: $missingNotification")

        return when {
            missingBluetooth -> OnboardingState.BLUETOOTH
            missingLocation -> OnboardingState.LOCATION
            missingNotification -> OnboardingState.NOTIFICATION
            else -> OnboardingState.ALL_SET
        }
    }

    fun requestCurrentStatePermissions() {
        Log.d(TAG, "requestCurrentStatePermissions called for state: $currentState")

        val permissionsToRequest = when (currentState) {
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

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Launching permission request for: $permissionsToRequest")
            permissionsLauncher?.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "No permissions to request for $currentState - moving to the next step")
            navigateToNextStep()
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
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
                handleCriticalPermissionsDenial(deniedCriticalPermissions)
            }
        }
    }

    private fun shouldShowNotificationPermission(): Boolean {
        return isFirstTimeLaunch &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !permissionsManager.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handleCriticalPermissionsDenial(deniedCritical: Set<String>) {
        val message = buildString {
            append("Critical permissions were denied. These are required for the app to function:\n")
            append(deniedCritical.joinToString("\n") { "- $it" })
            append("\n\nPlease grant these permissions in Settings to use the app.")
        }
        onboardingFailed(message)
    }

    private fun completeOnboarding() {
        Log.d(TAG, "Completing onboarding")
        if (AppConstants.DEBUG.ONBOARDING_RESET) {
            Log.d(TAG, "DEBUG MODE: Resetting onboarding completion")
            onboardingState.resetCompletion()
            return
        }
        onboardingState.markComplete()
        onboardingComplete()
        Log.d(TAG, "Onboarding completed, navigating to main app screen")
    }
}