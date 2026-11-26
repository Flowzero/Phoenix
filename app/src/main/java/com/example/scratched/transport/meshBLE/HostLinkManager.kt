package com.example.scratched.transport.meshBLE

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import com.example.scratched.constants.AppConstants.GATT
import com.example.scratched.transport.LinkListener
import com.example.scratched.transport.Transport

/**
 * Manages server-side connections: opens up a GATT-server, registers GATT-service,
 * accepts connection and transfers bytes
 */

@SuppressLint("MissingPermission")
internal class HostLinkManager(
    private val context: Context,

    /**
     * HostLink reports on events as they occur,
     * without knowing exactly who is processing them
     */
    private val listener: LinkListener,
    private val bluetoothManager: BluetoothManager
) : BluetoothGattServerCallback(), Transport {
    companion object {
        private const val TAG = "HostLinkManager"
    }

    private var gattServer: BluetoothGattServer? = null
    private var peer: BluetoothDevice? = null

    private var gattServiceRegistered = false
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

    override fun open() {
        val service = BluetoothGattService(
            GATT.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // service contains a characteristic (channel) and the characteristic has descriptor
        check(channel.addDescriptor(subscription) && service.addCharacteristic(channel)) {
            "Failed to create service with characteristic and its descriptor"
        }

        gattServer = bluetoothManager.openGattServer(context, this)
        check(gattServer?.addService(service) == true)
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
        // super.onServiceAdded(status, service)

        listener.dispatch(this) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gattServiceRegistered = true
                listener.onListening(this)
            } else {
                listener.onError(
                    this,
                    "Failed to register GATT-service"
                )
            }
        }
    }

    // for now: service one device per connection
    override fun onConnectionStateChange(
        device: BluetoothDevice?,
        status: Int,
        newState: Int
    ) {
        // super.onConnectionStateChange(device, status, newState)

        listener.dispatch(this) {
            // new device successfully connected
            if (status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothGatt.STATE_CONNECTED
            ) {
                // normally should not happen
                if (!gattServiceRegistered) {
                    gattServer?.cancelConnection(device)

                // Our server does not yet have a selected conversation partner
                } else if (peer == null) {
                    peer = device
                    listener.onConnected(this)

                // refuse connection from another device
                } else if (peer != device) {
                    gattServer?.cancelConnection(device)

                // a connection from known device
                } else if (peer == device) {
                    // do nothing
                }

            // process an error or disconnect of partner device
            } else if (peer == device) {
                subscribed = false

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onError(
                        this,
                        "Error occurred while BLE-connection"
                    )
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    listener.onDisconnected(this)
                }
            }
        }
    }

    private fun respond(
        device: BluetoothDevice,
        id: Int,
        accepted: Boolean
    ) {
        val status = if (accepted) {
            BluetoothGatt.GATT_SUCCESS
        } else {
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
        }

        check(
            gattServer?.sendResponse(
                device, id, status, 0, null
            ) == true || device != peer
        ) {
            "Failed to send GATT response"
        }
    }

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        listener.dispatch(this) {

            val accepted =
                device == peer &&
                        descriptor.uuid == subscription.uuid &&
                        offset == 0 &&
                        !preparedWrite &&
                        value.contentEquals(
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        )

            if (responseNeeded) {
                respond(device, requestId, accepted)
            }

            if (accepted && !subscribed) {
                subscribed = true
                listener.onReady(this)
            }
        }
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) = listener.dispatch(this) {

        val accepted =
            subscribed &&
                    device == peer &&
                    characteristic.uuid == channel.uuid &&
                    !preparedWrite &&
                    responseNeeded &&
                    offset == 0

        if (responseNeeded) {
            respond(device, requestId, accepted)
        }

        if (accepted) {
            listener.onDataReceived(this, value)
        }
    }

    override fun write(bytes: ByteArray): Boolean {

        val active = gattServer ?: return false
        val device = peer ?: return false

        if (!subscribed) return false

        return if (Build.VERSION.SDK_INT >= 33) {

            active.notifyCharacteristicChanged(
                device,
                channel,
                true,
                bytes
            ) == BluetoothStatusCodes.SUCCESS

        } else {

            channel.value = bytes

            active.notifyCharacteristicChanged(
                device,
                channel,
                true
            )
        }
    }

    override fun onNotificationSent(
        device: BluetoothDevice,
        status: Int
    ) = listener.dispatch(this) {

        if (device == peer) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onDataSent(this)
            } else {
                listener.onError(
                    this,
                    "Failed to send data"
                )
            }
        }
    }

    override fun close() {

        peer?.let {
            runCatching {
                gattServer?.cancelConnection(it)
            }
        }

        runCatching {
            gattServer?.close()
        }

        gattServer = null
        peer = null
        gattServiceRegistered = false
        subscribed = false
    }
}