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
import com.example.scratched.onboarding.AboutScreen
import com.example.scratched.onboarding.AllSetScreen
import com.example.scratched.onboarding.BluetoothPermissionScreen
import com.example.scratched.onboarding.LocationPermissionScreen
import com.example.scratched.onboarding.MainAppScreen
import com.example.scratched.onboarding.NotificationPermissionScreen
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.onboarding.WelcomeScreen
import com.example.scratched.ui.theme.ScratchedTheme
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    // ✅ ViewModel создаётся и внедряется автоматически (через Hilt или Factory)
    private val viewModel: MainViewModel by viewModels()

    // ✅ Лаунчер для запроса разрешений. Живёт в Activity, так как работает с Android Framework
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Передаём результат в ViewModel, а она уже отдаст его Координатору
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }

        // ✅ Наблюдаем за состоянием (StateFlow)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onboardingState.collect { state ->
                    Log.d("MainActivity", "Onboarding state changed to: $state")
                    // Дополнительная логика при смене состояния, если нужна
                }
            }
        }

        // ✅ Наблюдаем за одноразовыми событиями (SharedFlow)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    handleUiEvent(event)
                }
            }
        }
    }

    // ==================== Обработка событий от ViewModel ====================

    private fun handleUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.RequestPermissions -> {
                Log.d("MainActivity", "Launching permission request for: ${event.permissions.toList()}")
                permissionsLauncher.launch(event.permissions)
            }

            is UiEvent.NavigateToMainApp -> {
                Log.d("MainActivity", "Onboarding completed, navigating to main app")
                // startActivity(Intent(this, MainAppActivity::class.java))
                // finish()
                Toast.makeText(this, "Онбординг завершён!", Toast.LENGTH_SHORT).show()
            }

            is UiEvent.ShowError -> {
                Log.e("MainActivity", "Onboarding error: ${event.message}")
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
                // Или показать Snackbar / открыть настройки:
                // openAppSettings()
            }

            is UiEvent.EnableBluetooth -> TODO()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // ==================== UI Compose ====================

    @Composable
    private fun OnboardingFlowScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
        // ✅ Собираем состояние из ViewModel
        val onboardingState by viewModel.onboardingState.collectAsState()

        when (onboardingState) {
            is OnboardingState.WELCOME -> {
                WelcomeScreen(
                    modifier = modifier,
                    onContinue = { viewModel.onNextClicked() } // ✅ Вызов через ViewModel
                )
            }
            is OnboardingState.ABOUT -> {
                AboutScreen(
                    modifier = modifier,
                    onContinue = { viewModel.onNextClicked() }
                )
            }
            is OnboardingState.BLUETOOTH -> {
                BluetoothPermissionScreen(
                    modifier = modifier,
                    onContinue = { viewModel.onRequestPermissionsClicked() } // ✅ Запрос через ViewModel
                )
            }
            is OnboardingState.LOCATION -> {
                LocationPermissionScreen(
                    modifier = modifier,
                    onContinue = { viewModel.onRequestPermissionsClicked() }
                )
            }
            is OnboardingState.NOTIFICATION -> {
                NotificationPermissionScreen(
                    modifier = modifier,
                    onContinue = { viewModel.onRequestPermissionsClicked() }
                )
            }
            is OnboardingState.ALL_SET -> {
                AllSetScreen(
                    modifier = modifier,
                    onContinue = { viewModel.onNextClicked() }
                )
            }
            is OnboardingState.COMPLETED -> {
                MainAppScreen(modifier = modifier)
            }
            is OnboardingState.FAILED -> {
                // ✅ Обработка нового состояния ошибки
                /*FailedScreen(
                    modifier = modifier,
                    errorMessage = (onboardingState as OnboardingState.Failed).errorMessage,
                    onOpenSettings = { openAppSettings() }
                )

                 */
                TODO()
            }
        }
    }
}