package com.example.scratched

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PermissionsManager(private val context: Context) {

    companion object {
        private val TAG = "PermissionsManager"
    }

    fun getRequiredPermissions() : List<String> {
        val permissions = mutableListOf<String>()

        // Bluetooth permissions (API level dependent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
        } else {
            // legacy Bluetooth permissions
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }

        // Location permissions
        permissions.addAll(listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))

        return permissions
    }

    fun getOptionalPermissions() : List<String> {
        val optional = mutableListOf<String>()

        // notifications on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            optional.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return optional
    }

    fun getBluetoothPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // Bluetooth permissions (API level dependent)
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
        val permissions = mutableListOf<String>()

        // Location permissions (required for Bluetooth Low Energy scanning)
        permissions.addAll(listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))

        return permissions
    }

    fun getNotificationPermissions(): List<String> {
        val optional = mutableListOf<String>()

        // notifications on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            optional.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return optional
    }

    fun isPermissionGranted(permission: String) : Boolean {
        return ContextCompat
            .checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun areAllRequiredPermissionsGranted() : Boolean {
        return getRequiredPermissions().all { isPermissionGranted(it) }
    }

    fun getMissingRequiredPermissions() : List<String> {
        return getRequiredPermissions().filter { !isPermissionGranted(it) }
    }

    fun areAnyMissingPermissions(permissions: List<String>): Boolean {
        return permissions.any {!isPermissionGranted(it)}
    }

    fun hasBluetoothPermissions() : Boolean {
        return getBluetoothPermissions().all { isPermissionGranted(it) }
    }
}