package com.example.scratched

import android.content.Intent
import android.net.Uri
import android.provider.Settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

import com.example.scratched.onboarding.*

import android.util.Log
import com.example.scratched.onboarding.AppFlowCoordinator
import com.example.scratched.ui.theme.ScratchedTheme


class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionsManager
    private lateinit var onboardingState: OnboardingStateRepository
    // private lateinit var onboardingManager: OnboardingManager
    private lateinit var  appFlowCoordinator: AppFlowCoordinator
    //private lateinit var bluetoothStatusManager: BluetoothStatusManager
    //private lateinit var locationStatusManager: LocationStatusManager

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //installSplashScreen()
        enableEdgeToEdge()

        // Initialize permission manager
        permissionManager = PermissionsManager(this)

        // Initialize core components
        /*bluetoothStatusManager = BluetoothStatusManager(
            activity = this,
            context = this,
            onBluetoothEnabled = ::handleBluetoothEnabled,
            onBluetoothDisabled = ::handleBluetoothDisabled
        )
        //locationStatusManager = LocationStatusManager(
            activity = this,
            context = this,
            onLocationEnabled = ::handleLocationEnabled,
            onLocationDisabled = ::handleLocationDisabled
        )

         */

        onboardingState = OnboardingPrefsRepository(
            context = this
        )

        /*
        // Initialize onboarding manager
        onboardingManager = OnboardingManager(
            activity = this,
            permissionManager = permissionManager,
            onboardingComplete = ::handleOnboardingComplete,
            onboardingFailed = ::handleOnboardingFailed,
            updateOnboardingState = { state ->
                mainViewModel.updateOnboardingState(state)
            }
        )
         */

        appFlowCoordinator = AppFlowCoordinator(
            activity = this,
            permissionsManager = permissionManager,
            onboardingState = onboardingState,
            onboardingComplete = ::handleOnboardingComplete,
            onboardingFailed = ::handleOnboardingFailed,
            updateOnboardingState = { state ->
                mainViewModel.updateOnboardingState(state)
            }
        )

        setContent {
            ScratchedTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OnboardingFlowScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }

        // Start onboarding when activity is created
        // onboardingManager.startAppFlow()
        appFlowCoordinator.startAppFlow()



        // Collect state changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.onboardingState.collect { state ->
                    handleOnboardingStateChange(state)
                }
            }
        }
    }

    @Composable
    private fun OnboardingFlowScreen(modifier: Modifier = Modifier) {
        val onboardingState = mainViewModel.onboardingState.collectAsState().value

        when(onboardingState) {
            OnboardingState.WELCOME -> {
                WelcomeScreen(
                    modifier = modifier,
                    onContinue = {
                        Log.d("MainActivity", "Welcome continue clicked")
                        //onboardingManager.navigateToNextStep()
                        appFlowCoordinator.navigateToNextStep()
                    }
                )
            }

            OnboardingState.ABOUT -> {
                AboutScreen(
                    modifier = modifier,
                    onContinue = {
                        Log.d("MainActivity", "About continue clicked")
                        //onboardingManager.navigateToNextStep()
                        appFlowCoordinator.navigateToNextStep()
                    }
                )
            }

            OnboardingState.BLUETOOTH -> {
                BluetoothPermissionScreen(
                    modifier = modifier,
                    onContinue = {
                        Log.d("MainActivity", "Bluetooth continue clicked")
                        //onboardingManager.requestCurrentStatePermissions()
                        appFlowCoordinator.requestCurrentStatePermissions()
                    }
                )
            }

            OnboardingState.LOCATION -> {
                LocationPermissionScreen(
                    modifier = modifier,
                    onContinue = {
                        Log.d("MainActivity", "Location continue clicked")
                        appFlowCoordinator.requestCurrentStatePermissions()
                    }
                )
            }

            OnboardingState.NOTIFICATION -> {
                NotificationPermissionScreen(
                    modifier = modifier,
                    onContinue = {
                        Log.d("MainActivity", "Notification continue clicked")
                        //onboardingManager.requestCurrentStatePermissions()
                        appFlowCoordinator.requestCurrentStatePermissions()
                    }
                )
            }

            OnboardingState.ALL_SET -> {
                AllSetScreen(
                    modifier = modifier,
                    onContinue = {
                        Log.d("MainActivity", "All set continue clicked")
                        //onboardingManager.navigateToNextStep()
                        appFlowCoordinator.navigateToNextStep()
                    }
                )
            }
            OnboardingState.COMPLETED -> {
                MainAppScreen(
                    modifier = Modifier
                )
            }
        }
    }

    private fun handleBluetoothEnabled() {
        //
    }

    private fun handleBluetoothDisabled(message: String) {
        //
    }

    private fun handleLocationEnabled() {
        //
    }

    private fun handleLocationDisabled(message: String) {
        //
    }

    private fun handleOnboardingStateChange(state: OnboardingState) {
        Log.d("MainActivity", "Onboarding state changed to: $state")
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun handleOnboardingComplete() {
        Log.d("MainActivity", "Onboarding completed - navigating to main app")
        // Initialize app logic and navigate to main app
        // startActivity(Intent(this, MainAppActivity::class.java))
        // finish()
    }

    private fun handleOnboardingFailed(message: String) {
        Log.e("MainActivity", "Onboarding failed: $message")
        openAppSettings()

    }
}
