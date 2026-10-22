package com.example.scratched.ui

import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OnboardingScreensTest(screen: ScreenCase) : OnboardingScreensContract(screen) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = OnboardingScreensContract.screens()
    }
}
