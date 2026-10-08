package com.example.scratched.mesh.stateManagers


import android.util.Log
import android.content.Intent
import android.content.Context
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.bluetooth.BluetoothAdapter
import androidx.core.content.ContextCompat

import com.example.scratched.mesh.BluetoothAdapterWrapper
import com.example.scratched.mesh.BluetoothDisabledException
import com.example.scratched.mesh.BluetoothNotSupported
import com.example.scratched.mesh.BluetoothState

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Checks Bluetooth status (enabled/disabled/not_supported)
 */

class BluetoothStateManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapterWrapper,
) {
    companion object {
        const val TAG = "BluetoothStatusManager"
    }

    private val _bluetoothStateFlow = MutableStateFlow(bluetoothAdapter.getState())

    // accessible
    val bluetoothStateFlow: StateFlow<BluetoothState> = _bluetoothStateFlow.asStateFlow()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val currentState = _bluetoothStateFlow.value
                val newState = bluetoothAdapter.getState()

                if (newState != currentState) {
                    Log.d(TAG, "Bluetooth state changed on: $newState")
                    _bluetoothStateFlow.value = newState
                }
            }
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            bluetoothStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "Started monitoring Bluetooth state")
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
            Log.d(TAG, "Stopped monitoring Bluetooth state")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered or already unregistered")
        }
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