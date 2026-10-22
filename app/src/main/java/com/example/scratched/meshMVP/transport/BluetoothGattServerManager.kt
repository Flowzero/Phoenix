package com.example.scratched.meshMVP.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.os.ParcelUuid
import com.example.scratched.constants.AppConstants
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages GATT server side operations: advertising and server-side connections
 */

@Suppress("MissingPermission")
class BluetoothGattServerManager(
    private val context: Context,
    private val connectionScope: CoroutineScope,
    private val permissionsManager: PermissionsManager
) {
    companion object {
        private const val TAG = "BluetoothGattServerManager"
    }

    // Unable to test without Android-specific components, no need to use wrapper
    // Using raw Android API (BluetoothAdapter)

    // https://punchthrough.com/android-ble-guide/
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val bleAdvertiser: BluetoothLeAdvertiser? by lazy {
        bluetoothAdapter?.bluetoothLeAdvertiser
    }

    private var isAdvertising = false

    fun startAdvertising() {
        val data = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(AppConstants.GATT.SERVICE_UUID), byteArrayOf(0))
            .setIncludeTxPowerLevel(true)
            .setIncludeDeviceName(true)
            .build()

    }
}