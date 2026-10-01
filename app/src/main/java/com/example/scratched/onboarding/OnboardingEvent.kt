package com.example.scratched.onboarding

/**
 * Onboarding event is a one time command to UI, such as requesting specific permissions, navigating
 * to the main app screen or showing an error.
 */

sealed interface OnboardingEvent {
    data class RequestPermissions(val permissions: Array<String>) : OnboardingEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RequestPermissions) return false
            return permissions.contentEquals(other.permissions)
        }
        override fun hashCode(): Int = permissions.contentHashCode()
    }
    data class EnableBluetooth(val intent: android.content.Intent) : OnboardingEvent
    data class ShowError(val message: String) : OnboardingEvent
    object NavigateToMainApp : OnboardingEvent
}