package com.example.scratched.support

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import com.example.scratched.meshMVP.transport.BluetoothGattClientManager
import com.example.scratched.utilities.PermissionsManager
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlin.coroutines.ContinuationInterceptor

/** Real GATT manager with only Android radio APIs replaced. Used with Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerFixture(
    scope: CoroutineScope,
    val permissions: PermissionsManager = mockk(),
    hasAdapter: Boolean = true,
    hasScanner: Boolean = true
) {
    val context = mockk<Context>()
    val bluetoothManager = mockk<BluetoothManager>()
    val adapter = mockk<BluetoothAdapter>()
    val scanner = mockk<BluetoothLeScanner>()
    val callback = slot<ScanCallback>()
    val client: BluetoothGattClientManager

    init {
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns if (hasAdapter) bluetoothManager else null
        every { bluetoothManager.adapter } returns adapter
        every { adapter.bluetoothLeScanner } returns if (hasScanner) scanner else null
        every { scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), capture(callback)) } just Runs
        every { scanner.stopScan(any<ScanCallback>()) } just Runs
        // Actual PermissionsManager may be supplied by integration tests.
        if (isMockKMock(permissions)) {
            every { permissions.areAllRequiredPermissionsGranted() } returns true
            every { permissions.hasBluetoothPermissions() } returns true
        }
        val dispatcher = scope.coroutineContext[ContinuationInterceptor] as TestDispatcher
        client = BluetoothGattClientManager(context, scope, permissions) {
            10_000L + dispatcher.scheduler.currentTime
        }
    }

    fun result(
        address: String = "AA:BB:CC:DD:EE:01",
        name: String? = "Nearby phone",
        rssi: Int = -60,
        record: ScanRecord? = null,
        connectable: Boolean = true
    ): ScanResult {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns address
        every { device.name } returns name
        return mockk {
            every { this@mockk.device } returns device
            every { this@mockk.rssi } returns rssi
            every { scanRecord } returns record
            every { isConnectable } returns connectable
        }
    }
}
