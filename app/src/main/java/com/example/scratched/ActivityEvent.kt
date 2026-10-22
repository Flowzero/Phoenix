package com.example.scratched

import android.content.Intent

/**
 * Android-specif commands sent from ViewModel to Activity
 */

sealed interface ActivityEvent {
    data class RequestPermissions(val permissions: Array<String>): ActivityEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RequestPermissions) return false
            return permissions.contentEquals(other.permissions)
        }
        override fun hashCode(): Int = permissions.contentHashCode()
    }
    data class EnableBluetooth(val intent: Intent): ActivityEvent
    data class EnableLocation(val intent: Intent): ActivityEvent
    data class ShowError(val message: String): ActivityEvent
}