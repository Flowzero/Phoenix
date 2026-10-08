package com.example.scratched.mesh

import android.content.Intent
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BluetoothStateManagerTest {

    private lateinit var bluetoothAdapter: BluetoothAdapterWrapper
    private lateinit var manager: BluetoothStateManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        bluetoothAdapter = mockk()
        manager = BluetoothStateManager(bluetoothAdapter)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun ensureReadyForBLE_returnsSuccess_whenBluetoothIsEnabled() {
        every { bluetoothAdapter.getState() } returns BluetoothState.ENABLED

        val result = manager.ensureReadyForBLE()

        assertTrue(result.isSuccess)
    }

    @Test
    fun ensureReadyForBLE_returnsDisabledError_whenBluetoothIsDisabled() {
        every { bluetoothAdapter.getState() } returns BluetoothState.DISABLED

        val result = manager.ensureReadyForBLE()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BluetoothDisabledException)
    }

    @Test
    fun ensureReadyForBLE_returnsNotSupportedError_whenBluetoothIsNotSupported() {
        every { bluetoothAdapter.getState() } returns BluetoothState.NOT_SUPPORTED

        val result = manager.ensureReadyForBLE()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BluetoothNotSupported)
    }

    @Test
    fun getEnableBluetoothIntent_returnsIntentFromAdapter() {
        val expectedIntent = mockk<Intent>()
        every { bluetoothAdapter.getEnableIntent() } returns expectedIntent

        val actualIntent = manager.getEnableBluetoothIntent()

        assertSame(expectedIntent, actualIntent)
    }

    @Test
    fun getEnableBluetoothIntent_returnsNull_whenAdapterCannotProvideIntent() {
        every { bluetoothAdapter.getEnableIntent() } returns null

        val actualIntent = manager.getEnableBluetoothIntent()

        assertNull(actualIntent)
    }

    @Test
    fun getCurrentBluetoothState_returnsStateFromAdapter() {
        every { bluetoothAdapter.getState() } returns BluetoothState.ENABLED

        val state = manager.getCurrentBluetoothState()

        assertSame(BluetoothState.ENABLED, state)
    }

    @Test
    fun getAdapterNameForUI_returnsAdapterName() {
        every { bluetoothAdapter.getAdapterName() } returns "Test device"

        val name = manager.getAdapterNameForUI()

        assertEquals("Test device", name)
    }

    @Test
    fun getAdapterNameForUI_returnsUnknownDevice_whenAdapterNameIsMissing() {
        every { bluetoothAdapter.getAdapterName() } returns null

        val name = manager.getAdapterNameForUI()

        assertEquals("Unknown Device", name)
    }

    @Test
    fun isEnabled_returnsTrue_whenAdapterIsEnabled() {
        every { bluetoothAdapter.isEnabled() } returns true

        assertTrue(manager.isEnabled())
    }

    @Test
    fun isEnabled_returnsFalse_whenAdapterIsDisabled() {
        every { bluetoothAdapter.isEnabled() } returns false

        assertFalse(manager.isEnabled())
    }
}
