package com.example.scratched.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class UsableBluetoothAdapterTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var packageManager: PackageManager
    private lateinit var adapterWrapper: UsableBluetoothAdapter

    @Before
    fun setUp() {
        context = mockk()
        bluetoothManager = mockk()
        bluetoothAdapter = mockk()
        packageManager = mockk()

        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.packageManager } returns packageManager
        every { bluetoothManager.adapter } returns bluetoothAdapter

        adapterWrapper = UsableBluetoothAdapter(context)
    }

    @Test
    fun isSupported_returnsTrue_whenBluetoothAdapterExists() {
        assertTrue(adapterWrapper.isSupported())
    }

    @Test
    fun isSupported_returnsFalse_whenBluetoothManagerIsUnavailable() {
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns null

        assertFalse(adapterWrapper.isSupported())
    }

    @Test
    fun isEnabled_returnsTrue_whenAdapterIsEnabled() {
        every { bluetoothAdapter.isEnabled } returns true

        assertTrue(adapterWrapper.isEnabled())
    }

    @Test
    fun isEnabled_returnsFalse_whenAdapterIsDisabled() {
        every { bluetoothAdapter.isEnabled } returns false

        assertFalse(adapterWrapper.isEnabled())
    }

    @Test
    fun isEnabled_returnsFalse_whenPermissionCheckThrowsSecurityException() {
        every { bluetoothAdapter.isEnabled } throws SecurityException("Permission denied")

        assertFalse(adapterWrapper.isEnabled())
    }

    @Test
    fun getEnableIntent_returnsBluetoothEnableAction() {
        val intent = adapterWrapper.getEnableIntent()

        assertEquals(BluetoothAdapter.ACTION_REQUEST_ENABLE, intent?.action)
    }

    @Test
    fun getState_returnsNotSupported_whenDeviceHasNoBluetoothLE() {
        every {
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } returns false

        assertSame(BluetoothState.NOT_SUPPORTED, adapterWrapper.getState())
    }

    @Test
    fun getState_returnsEnabled_whenBluetoothLEExistsAndAdapterIsEnabled() {
        every {
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } returns true
        every { bluetoothAdapter.isEnabled } returns true

        assertSame(BluetoothState.ENABLED, adapterWrapper.getState())
    }

    @Test
    fun getState_returnsDisabled_whenBluetoothLEExistsAndAdapterIsDisabled() {
        every {
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } returns true
        every { bluetoothAdapter.isEnabled } returns false

        assertSame(BluetoothState.DISABLED, adapterWrapper.getState())
    }

    @Test
    fun getAdapterName_returnsAdapterName() {
        every { bluetoothAdapter.name } returns "Test phone"

        assertEquals("Test phone", adapterWrapper.getAdapterName())
    }

    @Test
    fun getAdapterName_returnsNull_whenPermissionCheckThrowsSecurityException() {
        every { bluetoothAdapter.name } throws SecurityException("Permission denied")

        assertNull(adapterWrapper.getAdapterName())
    }
}
