package com.example.scratched

import com.example.scratched.meshMVP.transport.DiscoverySnapshot
import com.example.scratched.meshMVP.transport.ScanPhase

/** UI projection only. The transport layer does not import Compose or these debug models. */
internal fun DiscoverySnapshot.toUiState(current: MainScreenUiState): MainScreenUiState = current.copy(
    scanStatus = when (scan.phase) {
        ScanPhase.IDLE -> BleScanStatus.IDLE
        ScanPhase.WAITING -> BleScanStatus.WAITING
        ScanPhase.SCANNING -> BleScanStatus.SCANNING
        ScanPhase.STOPPED -> BleScanStatus.STOPPED
        ScanPhase.PERMISSION_REQUIRED -> BleScanStatus.PERMISSION_REQUIRED
        ScanPhase.BLUETOOTH_DISABLED -> BleScanStatus.BLUETOOTH_DISABLED
        ScanPhase.LOCATION_DISABLED -> BleScanStatus.LOCATION_REQUIRED
        ScanPhase.UNSUPPORTED -> BleScanStatus.UNSUPPORTED
        ScanPhase.ERROR -> BleScanStatus.ERROR
    },
    errorMessage = scan.error,
    scanStartedAtMillis = startedAtMillis,
    discoveredDeviceCount = devices.size,
    devices = devices.map { device ->
        val observation = device.observation
        BleDeviceUiModel(
            address = observation.address,
            name = observation.name,
            rssi = observation.rssi,
            txPower = observation.txPower,
            serviceUuids = observation.serviceUuids.map { it.toString() },
            manufacturerData = observation.manufacturerData.mapValues { it.value.toByteArray() },
            isConnectable = observation.isConnectable,
            firstSeenAtMillis = device.firstSeenAtMillis,
            lastSeenAtMillis = device.lastSeenAtMillis,
            scanCount = device.scanCount
        )
    }
)
