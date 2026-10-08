package com.example.scratched.utilities

import android.Manifest
import android.app.Application
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PermissionsManagerTest {

    private lateinit var application: Application
    private lateinit var permissionsManager: PermissionsManager

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        permissionsManager = PermissionsManager(application)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun getBluetoothPermissions_returnsLegacyPermissions_onAndroid11() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ),
            permissionsManager.getBluetoothPermissions()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun getBluetoothPermissions_returnsModernPermissions_onAndroid12() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ),
            permissionsManager.getBluetoothPermissions()
        )
    }

    @Test
    fun getLocationPermissions_returnsCoarseAndFineLocation() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            permissionsManager.getLocationPermissions()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun getNotificationPermissions_returnsEmptyList_beforeAndroid13() {
        assertTrue(permissionsManager.getNotificationPermissions().isEmpty())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun getNotificationPermissions_returnsPostNotifications_onAndroid13() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            permissionsManager.getNotificationPermissions()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun getRequiredPermissions_combinesLegacyBluetoothAndLocation_onAndroid11() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            permissionsManager.getRequiredPermissions()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun getRequiredPermissions_combinesModernBluetoothAndLocation_onAndroid12() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            permissionsManager.getRequiredPermissions()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun areAllRequiredPermissionsGranted_returnsTrue_whenEveryPermissionIsGranted() {
        val requiredPermissions = permissionsManager.getRequiredPermissions()
        shadowOf(application).grantPermissions(*requiredPermissions.toTypedArray())

        assertTrue(permissionsManager.areAllRequiredPermissionsGranted())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun getMissingRequiredPermissions_returnsOnlyDeniedPermission() {
        val requiredPermissions = permissionsManager.getRequiredPermissions()
        val deniedPermission = Manifest.permission.ACCESS_FINE_LOCATION
        shadowOf(application).grantPermissions(*requiredPermissions.toTypedArray())
        shadowOf(application).denyPermissions(deniedPermission)

        assertEquals(
            listOf(deniedPermission),
            permissionsManager.getMissingRequiredPermissions()
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun hasBluetoothPermissions_reflectsCurrentGrantState() {
        val bluetoothPermissions = permissionsManager.getBluetoothPermissions()
        shadowOf(application).denyPermissions(*bluetoothPermissions.toTypedArray())

        assertFalse(permissionsManager.hasBluetoothPermissions())

        shadowOf(application).grantPermissions(*bluetoothPermissions.toTypedArray())

        assertTrue(permissionsManager.hasBluetoothPermissions())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun areAnyMissingPermissions_detectsDeniedPermission() {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        shadowOf(application).grantPermissions(*permissions.toTypedArray())

        assertFalse(permissionsManager.areAnyMissingPermissions(permissions))

        shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_SCAN)

        assertTrue(permissionsManager.areAnyMissingPermissions(permissions))
    }
}
