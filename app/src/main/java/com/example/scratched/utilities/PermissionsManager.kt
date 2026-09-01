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
 * RESPONSIBILITIES:
 *      - Providing lists of required and optional permissions based on the device's API level
 *      - Check the current grant status for a specific permissions or a group of permissions
 *
 * Note: The class only checks and reports permission states. Actual permissions requests are
 * handled by [OnboardingFlowCoordinator]
 */

class PermissionsManager(private val context: Context) {
    companion object {
        private const val TAG = "PermissionsManager"
    }

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
        // legacy Bluetooth permissions for API 30 and below
        } else {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }
        permissions.addAll(listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
        return permissions
    }

    fun getOptionalPermissions(): List<String> {
        val optional = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            optional.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return optional
    }

    fun getBluetoothPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
        } else {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }
        return permissions
    }

    fun getLocationPermissions(): List<String> {
        return listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

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