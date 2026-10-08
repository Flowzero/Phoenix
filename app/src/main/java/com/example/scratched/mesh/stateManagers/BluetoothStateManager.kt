package com.example.scratched.mesh.stateManagers

import android.content.Intent
import android.util.Log
import com.example.scratched.mesh.BluetoothAdapterWrapper
import com.example.scratched.mesh.BluetoothDisabledException
import com.example.scratched.mesh.BluetoothNotSupported
import com.example.scratched.mesh.BluetoothState

/**
 * Checks Bluetooth status (enabled/disabled/not_supported)
 */

class BluetoothStateManager(
    private val bluetoothAdapter: BluetoothAdapterWrapper,
) {
    companion object {
        const val TAG = "BluetoothStatusManager"
    }

    fun ensureReadyForBLE(): Result<Unit> {
        val state: BluetoothState = bluetoothAdapter.getState()

        return when (state) {
            is BluetoothState.ENABLED -> {
                Log.d(TAG, "Bluetooth is enabled and ready for BLE operations")
                return Result.success(Unit)
            }

            is BluetoothState.DISABLED -> {
                Log.d(TAG, "Bluetooth is disabled")
                return Result.failure(BluetoothDisabledException())
            }

            is BluetoothState.NOT_SUPPORTED -> {
                Log.w(TAG, "Bluetooth is not supported on this device")
                return Result.failure(BluetoothNotSupported())
            }
        }
    }

    fun getEnableBluetoothIntent(): Intent? {
        return bluetoothAdapter.getEnableIntent().also { intent ->
            if (false) {
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

    fun isEnabled(): Boolean {
        return bluetoothAdapter.isEnabled()
    }
}