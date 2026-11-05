package com.example.scratched.utilities

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

// for documentation
import com.example.scratched.onboarding.OnboardingFlowCoordinator

/**
 * Centralized permissions management
 *
 * PermissionsManger responsible for:
 * * Providing lists of required and optional permissions based on the device's API level
 * *Check the current grant status for a specific permissions or a group of permissions
 *
 * Note: The class only checks and reports permission states. Actual permissions requests are
 * handled by [OnboardingFlowCoordinator]
 */

class PermissionsManager(private val context: Context) {
    companion object {
        private const val TAG = "PermissionsManager"
    }

    fun getRequiredPermissions(): List<String> = getBluetoothPermissions() + getLocationPermissions()

    fun getOptionalPermissions(): List<String> = getNotificationPermissions()

    fun getBluetoothPermissions(): List<String> = BlePermissionPolicy.bluetooth(Build.VERSION.SDK_INT)

    fun getLocationPermissions(): List<String> = BlePermissionPolicy.location(Build.VERSION.SDK_INT)

    fun requiresLocationServices(): Boolean = BlePermissionPolicy.requiresLocationServices(Build.VERSION.SDK_INT)

    fun getNotificationPermissions(): List<String> {
        val optional = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            optional.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return optional
    }

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun areAllRequiredPermissionsGranted(): Boolean {
        return getRequiredPermissions().all { isPermissionGranted(it) }
    }

    fun getMissingRequiredPermissions(): List<String> {
        return getRequiredPermissions().filter { !isPermissionGranted(it) }
    }

    fun areAnyMissingPermissions(permissions: List<String>): Boolean {
        return permissions.any { !isPermissionGranted(it) }
    }

    fun hasBluetoothPermissions(): Boolean {
        return getBluetoothPermissions().all { isPermissionGranted(it) }
    }
}