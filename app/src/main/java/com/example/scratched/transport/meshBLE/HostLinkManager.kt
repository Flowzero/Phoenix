package com.example.scratched.transport.meshBLE

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothManager
import com.example.scratched.constants.AppConstants.GATT

interface BleLinkListener {
    fun onDataReceived(source: HostLinkManager, data: ByteArray)
}

/**
 * Manages server-side connections: opens up a GATT-server, accepts connection
 * and exchanges data
 */
@SuppressLint("MissingPermission")
class HostLinkManager(
    private val context: Context,
    private val listener: BleLinkListener,
    private val bleLinkListener: BleLinkListener,
    private val bluetoothManager: BluetoothManager
) {
    companion object {
        private const val TAG = "HostLinkManager"
    }

    private var server: BluetoothGattServer? = null
    private var peer: BluetoothDevice? = null

    private var registered = false
    private var subscribed = false
    private var introduced = false
    private var confirming = false

    private var channel = BluetoothGattCharacteristic(
        GATT.CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        BluetoothGattCharacteristic.PROPERTY_WRITE
    )

    private val subscription = BluetoothGattDescriptor(
        GATT.DESCRIPTOR_UUID,
        BluetoothGattDescriptor.PERMISSION_WRITE
    )


}