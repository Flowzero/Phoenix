package com.example.scratched.mesh

import android.content.Context
import com.example.scratched.utilities.BluetoothAdapterWrapper

/**
 *
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


}

enum class BluetoothState {
    ENABLED,
    DISABLED,
    NOT_SUPPORTED
}