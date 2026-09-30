package com.example.scratched.onboarding

sealed class OnboardingState {
    object WELCOME: OnboardingState()
    object ABOUT: OnboardingState()
    object BLUETOOTH: OnboardingState()
    object LOCATION: OnboardingState()
    object NOTIFICATION: OnboardingState()
    object ALL_SET: OnboardingState()
    object COMPLETED: OnboardingState()

    data class FAILED(val errorMessage: String): OnboardingState()
}

sealed class OnboardingEvent {
    data class RequestPermissions(val permissions: Array<String>): OnboardingEvent() {
        override fun equals(other: Any?): Boolean {
            if (this == other) return true
            if (other !is RequestPermissions) return false
            return permissions.contentEquals(other.permissions)
        }

        override fun hashCode(): Int {
            return permissions.contentHashCode()
        }
    }

    object NavigateToMainApp: OnboardingEvent()
    data class ShowError(val message: String) : OnboardingEvent()
}
