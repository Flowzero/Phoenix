@file:Suppress("DEPRECATION")
package com.example.scratched.transport.meshBLE

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.example.scratched.constants.AppConstants.GATT
import com.example.scratched.transport.LinkListener
import com.example.scratched.transport.Transport

@SuppressLint("MissingPermission")
internal class ClientLinkManager(
    private val context: Context,
    private val listener: LinkListener,
    private val device: BluetoothDevice
) : BluetoothGattCallback(), Transport {
    private var client: BluetoothGatt? = null
    private var subscribed = false
    private var channel: BluetoothGattCharacteristic? = null

    override fun open() {
        client = checkNotNull(device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE))
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = listener.dispatch(this) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            subscribed = false
            listener.onError(this, "Собеседник недоступен ($status)")
        } else if (newState == BluetoothProfile.STATE_CONNECTED) {
            listener.onConnected(this)
            check(gatt.discoverServices()) { "Failed to discover GATT services" }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            subscribed = false
            listener.onDisconnected(this)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = listener.dispatch(this) {
        val characteristic = gatt.getService(GATT.SERVICE_UUID)?.getCharacteristic(GATT.CHARACTERISTIC_UUID)
        val descriptor = characteristic?.getDescriptor(GATT.DESCRIPTOR_UUID)
        if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null || descriptor == null) {
            listener.onError(this, "На этом телефоне не открыт сервис Phoenix")
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

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = listener.dispatch(this) {
        if (descriptor.uuid == GATT.DESCRIPTOR_UUID && !subscribed) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                subscribed = true
                listener.onReady(this)
            } else listener.onError(this, "Не удалось подписаться на индикации ($status)")
        }
    }

    override fun write(bytes: ByteArray): Boolean {
        if (!subscribed) return false
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

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = listener.dispatch(this) {
        if (characteristic.uuid == GATT.CHARACTERISTIC_UUID) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError(this, "Не удалось отправить данные ($status)")
            } else listener.onDataSent(this)
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid == GATT.CHARACTERISTIC_UUID) {
            val bytes = value.copyOf()
            listener.dispatch(this) {
                if (subscribed) listener.onDataReceived(this, bytes)
            }
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
        channel = null
        subscribed = false
    }
}
