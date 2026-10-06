package com.example.scratched.onboarding

/**
 * Onboarding event is a one time command to Activity, such as requesting specific permissions,
 * passing request to open app settings or showing an error.
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
    object RequestToOpenAppSettings: OnboardingEvent
    object RequestToEnableBluetooth: OnboardingEvent
    data class ShowError(val message: String): OnboardingEvent
}