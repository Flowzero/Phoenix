package com.example.scratched.mesh

import android.util.Log
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Checks Bluetooth status (enabled/disabled/not_supported) before
 * performing a BLE operation
 */

class BluetoothStateManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapterWrapper,
) {
    companion object {
        const val TAG = "BluetoothStatusManager"
    }

    fun ensureReadyForBLE(): Result<Unit> {
        val state = bluetoothAdapter.getState()

        return when (state) {
            is BluetoothState.ENABLED -> {
                Log.d(TAG, "Bluetooth is enabled and ready for BLE operations")
                Result.success(Unit)
            }

            is BluetoothState.DISABLED -> {
                Log.d(TAG, "Bluetooth is disabled")
                Result.failure(BluetoothDisabledException())
            }

            is BluetoothState.NOT_SUPPORTED -> {
                Log.w(TAG, "Bluetooth is not supported on this device")
                Result.failure(BluetoothNotSupported())
            }
        }
    }

    fun getEnableBluetoothIntent(): Intent? {
        return bluetoothAdapter.getEnableIntent().also { intent ->
            if (intent == null) {
                Log.d(TAG, "Cannot provide Enable Intent: missing permissions or adapter is unavailable")
            }
        }
    }

    fun getCurrentBluetoothState(): BluetoothState {
        return bluetoothAdapter.getState()
    }

    fun getAdapterNameForUI(): String? {
        return bluetoothAdapter.getAdapterName() ?: "Unknown Device"
    }


}
