package com.example.scratched.mesh

sealed class BluetoothState {
    object DISABLED: BluetoothState()
    object ENABLED:  BluetoothState()
    object NOT_SUPPORTED:  BluetoothState()
}