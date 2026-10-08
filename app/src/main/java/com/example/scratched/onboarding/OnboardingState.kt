package com.example.scratched.onboarding

/**
 * Represents the screens that are shown at each stage of onboarding process, starting with the
 * "Welcome" screen and ending with the "Completed" screen
 */

sealed class OnboardingState {
    object WELCOME: OnboardingState()
    object ABOUT: OnboardingState()
    object BLUETOOTH: OnboardingState()
    object ENABLE_BLUETOOTH: OnboardingState()
    object RUQUIRED_PERMISSION_REJECTED: OnboardingState()
    object LOCATION: OnboardingState()
    object NOTIFICATION: OnboardingState()
    object ALL_SET: OnboardingState()
    object COMPLETED: OnboardingState()

    data class FAILED(val errorMessage: String): OnboardingState()
}
