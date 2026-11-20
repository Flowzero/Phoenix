@file:Suppress("DEPRECATION")
package com.example.scratched.meshMVP.transport.chat

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.example.scratched.constants.AppConstants.GATT

@SuppressLint("MissingPermission")
internal class ClientLink(
    private val chat: DirectChat,
    private val context: Context,
    private val device: BluetoothDevice
) : BluetoothGattCallback(), ChatLink {
    private var client: BluetoothGatt? = null
    private var identityWritten = false
    private var identityReceived = false
    private var channel: BluetoothGattCharacteristic? = null

    override fun open() {
        client = checkNotNull(device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE))
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = chat.event(this) {
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            check(gatt.discoverServices())
        } else chat.end("Собеседник недоступен")
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = chat.event(this) {
        val characteristic = gatt.getService(GATT.SERVICE_UUID)?.getCharacteristic(GATT.CHARACTERISTIC_UUID)
        val descriptor = characteristic?.getDescriptor(GATT.DESCRIPTOR_UUID)
        if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null || descriptor == null) {
            chat.end("На этом телефоне не открыт чат Phoenix")
        } else {
            channel = characteristic
            check(gatt.setCharacteristicNotification(characteristic, true))
            val enable = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                check(gatt.writeDescriptor(descriptor, enable) == BluetoothStatusCodes.SUCCESS)
            } else {
                descriptor.value = enable
                check(gatt.writeDescriptor(descriptor))
            }
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = chat.event(this) {
        if (status == BluetoothGatt.GATT_SUCCESS) check(write(chat.introduction())) else chat.end("Не удалось подписаться на сообщения")
    }

    override fun write(bytes: ByteArray): Boolean {
        val active = client ?: return false
        val characteristic = channel ?: return false
        val mode = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= 33) {
            active.writeCharacteristic(characteristic, bytes, mode) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = mode
            characteristic.value = bytes
            active.writeCharacteristic(characteristic)
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = chat.event(this) {
        if (status != BluetoothGatt.GATT_SUCCESS) chat.end("Не удалось отправить сообщение")
        else if (!identityWritten) {
            identityWritten = true
            if (identityReceived) chat.ready()
        } else chat.acknowledged()
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid == GATT.CHARACTERISTIC_UUID) chat.event(this) {
            if (!identityReceived) {
                chat.identify(value)
                identityReceived = true
                if (identityWritten) chat.ready()
            } else chat.receive(value)
        }
    }

    @Deprecated("Callback for Android 10–12")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        characteristic.value?.copyOf()?.let { onCharacteristicChanged(gatt, characteristic, it) }
    }

    override fun close() {
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
        client = null
    }
}
