package com.example.scratched


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.example.scratched.utilities.stateManagers.BluetoothStateManager
import com.example.scratched.utilities.bluetooth.UsableBluetoothAdapter
import com.example.scratched.utilities.stateManagers.UsableLocationAdapter
import com.example.scratched.utilities.stateManagers.LocationStateManager
import com.example.scratched.onboarding.AboutScreen
import com.example.scratched.onboarding.AllSetScreen

import com.example.scratched.onboarding.BluetoothPermissionScreen
import com.example.scratched.onboarding.EnableBluetoothScreen
import com.example.scratched.onboarding.EnableLocationScreen
import com.example.scratched.onboarding.GrantPermissionsManually
import com.example.scratched.onboarding.LocationPermissionScreen
import com.example.scratched.onboarding.MainAppScreen

import com.example.scratched.onboarding.NotificationPermissionScreen
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.onboarding.OnboardingPrefsRepository
import com.example.scratched.onboarding.WelcomeScreen
import com.example.scratched.ui.theme.ScratchedTheme
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.launch

import com.example.scratched.BleDeviceUiModel
import com.example.scratched.BleScanStatus
import com.example.scratched.MainScreenUiState
import com.example.scratched.meshMVP.transport.BluetoothGattClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private val managerScope = CoroutineScope(Dispatchers.IO+ SupervisorJob())

    private val viewModel: MainViewModel by this.viewModels {
        MainViewModelFactory(
            permissionsManager = PermissionsManager(this),
            bluetoothStateManager = BluetoothStateManager(
                this,
                UsableBluetoothAdapter(this)),
            locationStateManager = LocationStateManager(UsableLocationAdapter(this)),
            repository = OnboardingPrefsRepository(this),
            gattClientManager = BluetoothGattClientManager(
                context = this,
                connectionScope = managerScope,
                permissionsManager = PermissionsManager(this))
        )
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionsResult(result)
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val isSuccess = (result.resultCode == RESULT_OK)
        viewModel.onBluetoothEnableResult(isSuccess)
    }

    private val enableLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onLocationSettingsClosed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScratchedTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OnboardingFlowScreen(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onboardingState.collect { state ->
                    Log.d(TAG, "Onboarding state changed to: ${state.toString()}")
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activityEvents.collect { event ->
                    handleActivityEvent(event)
                }
            }
        }
    }

    private fun handleActivityEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.RequestPermissions -> {
                Log.d(TAG, "Launching permission request for: ${event.permissions.toList()}")
                permissionsLauncher.launch(event.permissions)
            }
            is ActivityEvent.EnableBluetooth -> {
                enableBluetoothLauncher.launch(event.intent)
            }
            is ActivityEvent.EnableLocation -> {
                enableLocationLauncher.launch(event.intent)
            }
            is ActivityEvent.ShowError -> {
                Log.e(TAG, "Error: ${event.message}")
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onGrantPermissionsManuallyClicked() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // If user updated the Bluetooth state in settings
        viewModel.refreshState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop scanning if activity is killed! (FOR NOW: TEST IMPL!)
        viewModel.onStopScanClicked()
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

            is OnboardingState.RUQUIRED_PERMISSION_REJECTED -> GrantPermissionsManually(
                modifier = modifier,
                onContinue = { onGrantPermissionsManuallyClicked() },
                onCheck =  { viewModel.onNextClicked() }
            )

            is OnboardingState.ENABLE_BLUETOOTH -> EnableBluetoothScreen(
                modifier = modifier,
                onContinue = { viewModel.onEnableBluetoothClicked() }
            )

            is OnboardingState.LOCATION -> LocationPermissionScreen(
                modifier = modifier,
                onContinue = { viewModel.onRequestPermissionsClicked() }
            )
            is OnboardingState.ENABLE_LOCATION -> EnableLocationScreen(
                modifier = modifier,
                onContinue = { viewModel.onEnableLocationClicked() }
            )
            is OnboardingState.NOTIFICATION -> NotificationPermissionScreen(
                modifier = modifier,
                onContinue = { viewModel.onRequestPermissionsClicked() },
                onSkip =  { viewModel.onNextClicked() }
            )
            is OnboardingState.ALL_SET -> AllSetScreen(
                modifier = modifier,
                onContinue = { viewModel.onNextClicked() }
            )
            is OnboardingState.COMPLETED -> MainAppScreenContent(viewModel, modifier)

            is OnboardingState.FAILED -> {
                // FailedScreen(errorMessage = (onboardingState as OnboardingState.FAILED).errorMessage)
                Log.e(TAG, "Onboarding failed: ${(onboardingState as OnboardingState.FAILED).errorMessage}")
            }
        }
    }

    @Composable
    private fun MainAppScreenContent(viewModel: MainViewModel, modifier: Modifier = Modifier) {
        val uiState by viewModel.uiState.collectAsState()

        MainAppScreen(
            modifier = modifier,
            uiState = uiState,
            onStartScan = { viewModel.onStartScanClicked() },
            onStopScan = { viewModel.onStopScanClicked() },
            onDeviceClick = { device -> viewModel.onDeviceClick(device) },
            onRestartOnboarding = { viewModel.onRestartOnboardingClick() }
        )
    }
}