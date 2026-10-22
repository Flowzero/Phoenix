package com.example.scratched

// ==========================================
// МОДЕЛИ UI ДЛЯ BLE-СКАНИРОВАНИЯ
// ==========================================

enum class BleScanStatus {
    IDLE,
    SCANNING,
    STOPPED,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    ERROR
}

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

data class MainScreenUiState(
    val scanStatus: BleScanStatus = BleScanStatus.IDLE,
    val isBluetoothEnabled: Boolean = true,
    val hasRequiredPermissions: Boolean = true,
    val errorMessage: String? = null,
    val devices: List<BleDeviceUiModel> = emptyList(),
    val discoveredDeviceCount: Int = 0,
    val scanStartedAtMillis: Long? = null
)
