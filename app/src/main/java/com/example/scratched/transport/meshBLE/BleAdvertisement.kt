package com.example.scratched.transport.meshBLE

import java.nio.ByteBuffer
import java.util.Locale


object BleAdvertisement {
    const val VERSION = 1
    const val PAYLOAD_SIZE = 5

    fun encode(deviceId: Int): ByteArray {
        return ByteBuffer.allocate(PAYLOAD_SIZE)
            .put(VERSION.toByte())
            .putInt(deviceId)
            .array()
    }

    fun deviceName(serviceData: ByteArray?): String? {
        if (serviceData == null ||
            serviceData.size != BleAdvertisement.PAYLOAD_SIZE ||
            serviceData[0].toInt() != BleAdvertisement.VERSION) return null

        val id = ByteBuffer.wrap(serviceData, 1, 4).int
        return String.format(Locale.ROOT, "Phoenix-%08X", id)
    }
}