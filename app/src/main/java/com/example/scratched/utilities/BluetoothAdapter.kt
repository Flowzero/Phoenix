package com.example.scratched.utilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent

/**
 *
 */
interface BluetoothAdapterWrapper {
    fun isSupported(): Boolean
    fun isEnabled(): Boolean
    fun getEnableIntent(): Intent?
}

class UsableBluetoothAdapter(private val context: Context): BluetoothAdapterWrapper {
    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun isSupported(): Boolean {
        return (bluetoothAdapter != null)
    }

    override fun isEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    override fun getEnableIntent(): Intent? {
        return try {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } catch (e: SecurityException) {
            null
        }
    }
}