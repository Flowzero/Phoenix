package com.example.scratched.mesh

import android.content.Context
import android.util.Log

/**
 * Checks Bluetooth status (enabled/disabled) before
 * performing a BLE operation
 */
class AndroidBluetoothStatusManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapterWrapper,
    private val onBluetoothEnabled: () -> Unit,
    private val onBluetoothDisabled: (String) -> Unit
) {

    companion object {
        const val TAG = "BluetoothStatusManager"
    }

    fun onHandleBluetoothStatus(status: BluetoothState) {
        when (status) {
            BluetoothState.ENABLED -> {
                Log.d(TAG, "Bluetooth is enabled, proceeding")
                onBluetoothEnabled()
            }
            BluetoothState.DISABLED ->  {
                //requestEnableBluetooth()
            }
            BluetoothState.NOT_SUPPORTED -> {
                Log.e(TAG, "Bluetooth is not supported on this device")
                onBluetoothDisabled("This device does not support Bluetooth")
            }
        }
    }


}
