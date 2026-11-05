package com.example.scratched.meshMVP.transport

/** A scan observation, independent of how a screen displays or aggregates it. */
data class BleScanResult(
    val address: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int?,
    val serviceUuids: List<String>,
    val manufacturerData: Map<Int, ByteArray>,
    val isConnectable: Boolean?,
    val observedAtMillis: Long,
    val serviceData: Map<String, ByteArray> = emptyMap()
)
