package com.example.scratched.utilities

import org.junit.Assert.*
import org.junit.Test

class BlePermissionPolicyTest {
    @Test fun receivingAdvertisementsDoesNotRequireAdvertisingPermission() {
        for (api in 31..36) {
            assertEquals(setOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"), BlePermissionPolicy.scan(api).toSet())
        }
        for (api in listOf(29, 30)) {
            assertTrue(BlePermissionPolicy.scan(api).contains("android.permission.ACCESS_FINE_LOCATION"))
        }
    }

    @Test fun android10And11RequireLocationAndLegacyBluetooth() {
        for (api in listOf(29, 30)) {
            assertEquals(listOf("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN"), BlePermissionPolicy.bluetooth(api))
            assertTrue(BlePermissionPolicy.location(api).contains("android.permission.ACCESS_FINE_LOCATION"))
            assertTrue(BlePermissionPolicy.requiresLocationServices(api))
        }
    }

    @Test fun android12Through16UseNearbyDevicesWithoutLocation() {
        for (api in 31..36) {
            assertEquals(setOf("android.permission.BLUETOOTH_ADVERTISE", "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"), BlePermissionPolicy.bluetooth(api).toSet())
            assertTrue(BlePermissionPolicy.location(api).isEmpty())
            assertFalse(BlePermissionPolicy.requiresLocationServices(api))
        }
    }
}
