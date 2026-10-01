package com.example.scratched


import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.scratched.onboarding.AboutScreen
import com.example.scratched.onboarding.AllSetScreen
import com.example.scratched.onboarding.BluetoothPermissionScreen
import com.example.scratched.onboarding.LocationPermissionScreen
import com.example.scratched.onboarding.MainAppScreen
import com.example.scratched.onboarding.NotificationPermissionScreen
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.onboarding.OnboardingPrefsRepository
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.WelcomeScreen
import com.example.scratched.ui.theme.ScratchedTheme
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by this.viewModels {
        MainViewModelFactory(
            permissionsManager = PermissionsManager(this),
            repository = OnboardingPrefsRepository(this)
        )
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionsResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScratchedTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OnboardingFlowScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onboardingState.collect { state ->
                    Log.d(TAG, "Onboarding state changed to: $state")
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    handleUiEvent(event)
                }
            }
        }
    }

    private fun handleUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.RequestPermissions -> {
                Log.d("MainActivity", "Launching permission request for: ${event.permissions.toList()}")
                permissionsLauncher.launch(event.permissions)
            }
            is UiEvent.NavigateToMainApp -> {
                Log.d("MainActivity", "Onboarding completed")
                Toast.makeText(this, "Onboarding completed!", Toast.LENGTH_SHORT).show()
            }
            is UiEvent.ShowError -> {
                Log.e("MainActivity", "Error: ${event.message}")
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }

            is UiEvent.EnableBluetooth -> TODO()
        }
    }

    @Composable
    private fun OnboardingFlowScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
        val onboardingState by viewModel.onboardingState.collectAsState()

        when (onboardingState) {
            is OnboardingState.WELCOME -> WelcomeScreen(
                modifier = modifier,
                onContinue = { viewModel.onNextClicked() }
            )
            is OnboardingState.ABOUT -> AboutScreen(
                modifier = modifier,
                onContinue = { viewModel.onNextClicked() }
            )
            is OnboardingState.BLUETOOTH -> BluetoothPermissionScreen(
                modifier = modifier,
                onContinue = { viewModel.onRequestPermissionsClicked() }
            )
            is OnboardingState.LOCATION -> LocationPermissionScreen(
                modifier = modifier,
                onContinue = { viewModel.onRequestPermissionsClicked() }
            )
            is OnboardingState.NOTIFICATION -> NotificationPermissionScreen(
                modifier = modifier,
                onContinue = { viewModel.onRequestPermissionsClicked() }
            )
            is OnboardingState.ALL_SET -> AllSetScreen(
                modifier = modifier,
                onContinue = { viewModel.onNextClicked() }
            )
            is OnboardingState.COMPLETED -> MainAppScreen(modifier = modifier)
            is OnboardingState.FAILED -> {
                // FailedScreen(errorMessage = (onboardingState as OnboardingState.FAILED).errorMessage)
                Log.e("MainActivity", "Onboarding failed: ${(onboardingState as OnboardingState.FAILED).errorMessage}")
            }
        }
    }
}