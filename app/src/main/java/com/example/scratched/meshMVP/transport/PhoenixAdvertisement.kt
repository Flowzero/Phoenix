package com.example.scratched.meshMVP.transport

import java.nio.ByteBuffer
import java.util.Locale

/** Discovery payload v1: one version byte and a four-byte, process-local debug identifier. */
object PhoenixAdvertisement {
    const val VERSION = 1
    const val PAYLOAD_SIZE = 5

    fun encode(deviceId: Int): ByteArray = ByteBuffer.allocate(PAYLOAD_SIZE)
        .put(VERSION.toByte())
        .putInt(deviceId)
        .array()

    fun deviceName(serviceData: ByteArray?): String? {
        if (serviceData == null || serviceData.size != PAYLOAD_SIZE || serviceData[0].toInt() != VERSION) return null
        val id = ByteBuffer.wrap(serviceData, 1, 4).int
        return String.format(Locale.ROOT, "Phoenix-%08X", id)
    }
}
