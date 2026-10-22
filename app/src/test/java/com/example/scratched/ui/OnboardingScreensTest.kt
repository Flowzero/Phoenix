package com.example.scratched.ui

import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class OnboardingScreensTest(screen: ScreenCase) : OnboardingScreensContract(screen) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = OnboardingScreensContract.screens()
    }
}
