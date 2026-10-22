package com.example.scratched.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.scratched.onboarding.*
import com.example.scratched.ui.theme.ScratchedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Screen-component tests. They do not pretend to grant real system permissions. */
@RunWith(Parameterized::class)
abstract class OnboardingScreensContract(private val screen: ScreenCase) {
    @get:Rule val compose = createComposeRule()

    data class ScreenCase(
        val title: String,
        val button: String,
        val step: String,
        val content: @Composable (() -> Unit) -> Unit
    ) { override fun toString() = title }

    @Test fun screen_displaysItsStepAndInvokesPrimaryAction() {
        var clicks = 0
        compose.setContent { ScratchedTheme(dynamicColor = false) { screen.content { clicks++ } } }
        compose.onNodeWithText(screen.title).assertIsDisplayed()
        compose.onNodeWithText(screen.step).assertIsDisplayed()
        compose.onNodeWithText(screen.button).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    companion object {
        @JvmStatic
        fun screens() = listOf(
            ScreenCase("Welcome!", "Get started", "1 of 6") { WelcomeScreen(onContinue = it) },
            ScreenCase("A little about the app", "Continue", "2 of 6") { AboutScreen(onContinue = it) },
            ScreenCase("Connect nearby devices", "Allow Bluetooth", "3 of 6") { BluetoothPermissionScreen(onContinue = it) },
            ScreenCase("Bluetooth is turned off", "Turn on Bluetooth", "3 of 6") { EnableBluetoothScreen(onContinue = it) },
            ScreenCase("Enable location access", "Allow location", "4 of 6") { LocationPermissionScreen(onContinue = it) },
            ScreenCase("Location is turned off", "Turn on location", "4 of 6") { EnableLocationScreen(onContinue = it) },
            ScreenCase("Stay informed", "Allow notifications", "5 of 6") { NotificationPermissionScreen(onContinue = it, onSkip = {}) },
            ScreenCase("You're all set!", "Start using the app", "6 of 6") { AllSetScreen(onContinue = it) }
        ).map { arrayOf(it) }
    }
}
