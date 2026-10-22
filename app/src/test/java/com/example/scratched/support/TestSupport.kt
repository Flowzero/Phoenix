package com.example.scratched.support

import android.content.Intent
import android.util.Log
import com.example.scratched.BleDeviceUiModel
import com.example.scratched.utilities.bluetooth.BluetoothAdapterWrapper
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.utilities.stateManagers.LocationAdapterWrapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

class AndroidLogRule : TestWatcher() {
    override fun starting(description: Description) {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }
    override fun finished(description: Description) { unmockkStatic(Log::class) }
}

/** Only the radio boundary is fake. Managers and coordinators can remain real. */
class FakeBluetoothAdapter(var currentState: BluetoothState = BluetoothState.ENABLED) : BluetoothAdapterWrapper {
    var deviceName: String? = "Test phone"
    var enableRequest: Intent? = mockk(relaxed = true)
    override fun isSupported() = currentState != BluetoothState.NOT_SUPPORTED
    override fun isEnabled() = currentState == BluetoothState.ENABLED
    override fun getState() = currentState
    override fun getAdapterName() = deviceName
    override fun getEnableIntent() = enableRequest
}

class FakeLocationAdapter(var enabled: Boolean = true) : LocationAdapterWrapper {
    var enableRequest: Intent = mockk(relaxed = true)
    override fun isEnabled() = enabled
    override fun getEnableIntent() = enableRequest
}

fun device(
    address: String = "AA:BB:CC:DD:EE:01",
    name: String? = "Nearby phone",
    rssi: Int = -60,
    firstSeen: Long = 1000,
    lastSeen: Long = 1000
) = BleDeviceUiModel(address, name, rssi, null, emptyList(), emptyMap(), null, firstSeen, lastSeen, 1)
