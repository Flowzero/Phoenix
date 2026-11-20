@file:Suppress("DEPRECATION")
package com.example.scratched.meshMVP.transport.chat

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.example.scratched.constants.AppConstants.GATT

@SuppressLint("MissingPermission")
internal class HostLink(
    private val chat: DirectChat,
    private val context: Context,
    private val manager: BluetoothManager
) : BluetoothGattServerCallback(), ChatLink {
    private var server: BluetoothGattServer? = null
    private var peer: BluetoothDevice? = null
    private var registered = false
    private var subscribed = false
    private var introduced = false
    private var confirming = false
    private val channel = BluetoothGattCharacteristic(
        GATT.CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )
    private val subscription = BluetoothGattDescriptor(GATT.DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)

    override fun open() {
        val service = BluetoothGattService(GATT.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        check(channel.addDescriptor(subscription) && service.addCharacteristic(channel))
        server = manager.openGattServer(context, this)
        check(server?.addService(service) == true)
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) = chat.event(this) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            registered = true
            chat.listen()
        } else chat.end("Не удалось открыть сервис чата")
    }

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) = chat.event(this) {
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            if (!registered) server?.cancelConnection(device)
            else if (peer == null) {
                peer = device
                chat.accepting()
            } else if (peer != device) server?.cancelConnection(device)
        } else if (peer == device) chat.end("Соединение завершено")
    }

    private fun respond(device: BluetoothDevice, id: Int, accepted: Boolean) {
        val status = if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
        check(server?.sendResponse(device, id, status, 0, null) == true || device != peer)
    }

    override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) = chat.event(this) {
        val accepted = device == peer && descriptor.uuid == subscription.uuid && offset == 0 && !preparedWrite &&
            value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
        if (responseNeeded) respond(device, requestId, accepted)
        if (accepted) subscribed = true
    }

    override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) = chat.event(this) {
        val accepted = subscribed && device == peer && characteristic.uuid == channel.uuid &&
            !preparedWrite && responseNeeded && offset == 0
        if (accepted && !introduced) {
            chat.identify(value)
            introduced = true
            respond(device, requestId, true)
            confirming = true
            check(write(chat.introduction()))
        } else {
            if (accepted) chat.receive(value)
            if (responseNeeded) respond(device, requestId, accepted)
        }
    }

    override fun write(bytes: ByteArray): Boolean {
        val active = server ?: return false
        val device = peer ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            active.notifyCharacteristicChanged(device, channel, true, bytes) == BluetoothStatusCodes.SUCCESS
        } else {
            channel.value = bytes
            active.notifyCharacteristicChanged(device, channel, true)
        }
    }

    override fun onNotificationSent(device: BluetoothDevice, status: Int) = chat.event(this) {
        if (device == peer) {
            if (status != BluetoothGatt.GATT_SUCCESS) chat.end("Не удалось отправить сообщение")
            else if (confirming) {
                confirming = false
                chat.ready()
            } else chat.acknowledged()
        }
    }

    override fun close() {
        peer?.let { runCatching { server?.cancelConnection(it) } }
        runCatching { server?.close() }
        server = null
    }
}
