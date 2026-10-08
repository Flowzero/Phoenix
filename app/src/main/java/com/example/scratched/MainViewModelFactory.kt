package com.example.scratched


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scratched.mesh.BluetoothStateManager
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingStatusRepository
import com.example.scratched.utilities.PermissionsManager

class MainViewModelFactory(
    private val permissionsManager: PermissionsManager,
    private val bluetoothStateManager: BluetoothStateManager,
    private val repository: OnboardingStatusRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {

            val coordinator = OnboardingFlowCoordinator(
                permissionsManager = permissionsManager,
                bluetoothStateManager = bluetoothStateManager,
                onboardingStatusRepository = repository
            )
            return MainViewModel(
                onboardingFlowCoordinator = coordinator,
                bluetoothStatusManager = bluetoothStateManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}