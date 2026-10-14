package com.example.scratched.meshMVP.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.scratched.constants.AppConstants
import com.example.scratched.utilities.PermissionsManager

/**
 * Manages GATT client side operations: scanning and client-side connections
 */

@SuppressLint("MissingPermission")
class BluetoothGattClientManager(
    private val context: Context,
    private val permissionsManager: PermissionsManager

) {
    companion object {
        const val TAG = "BluetoothGattClientManager"
    }

    private var isScanning = false

    // Unable to test without Android-specific components, no need to use wrapper
    // Using raw Android API (BluetoothAdapter)

    // https://punchthrough.com/android-ble-guide/
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val scanFilter = ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString(AppConstants.GATT.SERVICE_UUID.toString())
    ).build()
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            with(result?.device) {
                Log.i("ScanCallback", "Found BLE device.\nName: ${this?.name ?: "Unnamed"}\nAddress: ${this?.address}")
            }
        }
    }

    private fun startScanning() {
        if (!permissionsManager.areAllRequiredPermissionsGranted()) {
            Log.e(TAG, "Required permissions are not granted!")
            return
        }

        if (bluetoothAdapter != null) {
            bleScanner?.startScan(null, scanSettings, scanCallback)
            isScanning = true
        } else {
            Log.e(TAG, "Bluetooth adapter is NULL! Cannot perform a scan")
            return
        }
    }

    private fun stopScanning() {
        if (bluetoothAdapter != null) {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
        }
    }
}