package com.example.scratched.onboarding

/**
 * Onboarding event is a one time command to Activity, such as requesting specific permissions,
 * or showing an error via pop up message. OnboardingEvent does not know anything about Android-
 * specific classes such as Activity or Intent
 */

sealed interface OnboardingEvent {
    data class RequestToRequestPermissions(val permissions: Array<String>) : OnboardingEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RequestToRequestPermissions) return false
            return permissions.contentEquals(other.permissions)
        }
        override fun hashCode(): Int = permissions.contentHashCode()
    }
    data class RequestToShowError(val message: String): OnboardingEvent
}