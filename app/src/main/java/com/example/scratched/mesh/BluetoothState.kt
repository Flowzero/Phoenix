package com.example.scratched.mesh

/**
 *  Final Bluetooth state: DISABLED, ENABLED and NOT_SUPPORTED
 */

sealed class BluetoothState {
    object DISABLED: BluetoothState()
    object ENABLED: BluetoothState()
    object NOT_SUPPORTED: BluetoothState()
}

class BluetoothDisabledException: Exception("Bluetooth adapter is disabled. User needs to enable it")
class BluetoothNotSupported: Exception("Bluetooth LE is not supported on this device")