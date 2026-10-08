package com.example.scratched


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scratched.mesh.stateManagers.BluetoothStateManager
import com.example.scratched.mesh.stateManagers.LocationStateManager
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingStatusRepository
import com.example.scratched.utilities.PermissionsManager

class MainViewModelFactory(
    private val permissionsManager: PermissionsManager,
    private val bluetoothStateManager: BluetoothStateManager,
    private val locationStateManager: LocationStateManager,
    private val repository: OnboardingStatusRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {

            val coordinator = OnboardingFlowCoordinator(
                permissionsManager = permissionsManager,
                bluetoothStateManager = bluetoothStateManager,
                locationStateManager = locationStateManager,
                onboardingStatusRepository = repository
            )
            return MainViewModel(
                onboardingFlowCoordinator = coordinator,
                bluetoothStatusManager = bluetoothStateManager,
                locationStateManager = locationStateManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}