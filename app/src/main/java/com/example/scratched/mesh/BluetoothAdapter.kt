package com.example.scratched.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface of BluetoothAdapter that allows to achieve better testability since separating
 * Android-specific code
 */

interface BluetoothAdapterWrapper {
    fun isSupported(): Boolean
    fun isEnabled(): Boolean
    fun getEnableIntent(): Intent?
    fun getState(): BluetoothState
    fun getAdapterName(): String?
}

/**
 * Implementation of [BluetoothAdapterWrapper] that contains Android specific code
 * @property context
 */

class UsableBluetoothAdapter(private val context: Context): BluetoothAdapterWrapper {
    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _currentBluetoothState = MutableStateFlow<BluetoothState>(BluetoothState.DISABLED)
    // accessible from ViewModel
    val currentBluetoothState: StateFlow<BluetoothState> = _currentBluetoothState.asStateFlow()

    override fun isSupported(): Boolean {
        return (bluetoothAdapter != null)
    }

    override fun isEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false // no Bluetooth permissions
        }
    }

    override fun getEnableIntent(): Intent? {
        return try {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } catch (e: SecurityException) {
            null // no permisisons? for request
        }
    }

    override fun getState(): BluetoothState {
        if (!context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BluetoothState.NOT_SUPPORTED
        }
        return try {
            if (isEnabled()) {
                BluetoothState.ENABLED
            } else {
                BluetoothState.DISABLED
            }
        } catch (e: SecurityException) {
            Log.e("Unexpected error checking Bluetooth state", e.message.toString())
            BluetoothState.DISABLED
        }
    }

    override fun getAdapterName(): String? {
        return try {
            bluetoothAdapter?.name
        } catch (e: SecurityException) {
            null
        }
    }
}