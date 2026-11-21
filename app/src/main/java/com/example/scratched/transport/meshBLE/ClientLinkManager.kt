package com.example.scratched.transport.meshBLE

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice


/**
 * Manages client-side connections: opens up a connection through which data is exchanged.
 * HostLinkManager accepts the connection
 */

@SuppressLint("MissingPermission")
class ClientLinkManager(
    private val context: Context,
    private val device: BluetoothDevice
) {

}