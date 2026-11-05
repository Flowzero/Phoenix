package com.example.scratched.utilities

/** Matches neverForLocation in the manifest: Android 12+ does not need location. */
internal object BlePermissionPolicy {
    fun bluetooth(api: Int): List<String> = if (api >= 31) listOf(
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_SCAN"
    ) else listOf("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN")

    fun scan(api: Int): List<String> = bluetooth(api)
        .filterNot { it == "android.permission.BLUETOOTH_ADVERTISE" } + location(api)

    fun location(api: Int): List<String> = if (api < 31) listOf(
        "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"
    ) else emptyList()

    fun requiresLocationServices(api: Int): Boolean = api < 31
}
