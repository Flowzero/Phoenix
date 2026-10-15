package com.example.scratched

data class BleDeviceUiModel(
    val address: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int?,
    val serviceUuids: List<String>,
    val manufacturerData: Map<Int, ByteArray>,
    val isConnectable: Boolean?,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val scanCount: Int
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Unknown device"
}