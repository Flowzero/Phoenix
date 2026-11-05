package com.example.scratched

import com.example.scratched.meshMVP.transport.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryUiMapperTest {
    @Test fun allTransportStatesHaveDebugUiProjection() {
        val mapping = mapOf(
            ScanPhase.IDLE to BleScanStatus.IDLE,
            ScanPhase.WAITING to BleScanStatus.WAITING,
            ScanPhase.SCANNING to BleScanStatus.SCANNING,
            ScanPhase.STOPPED to BleScanStatus.STOPPED,
            ScanPhase.PERMISSION_REQUIRED to BleScanStatus.PERMISSION_REQUIRED,
            ScanPhase.BLUETOOTH_DISABLED to BleScanStatus.BLUETOOTH_DISABLED,
            ScanPhase.LOCATION_DISABLED to BleScanStatus.LOCATION_REQUIRED,
            ScanPhase.UNSUPPORTED to BleScanStatus.UNSUPPORTED,
            ScanPhase.ERROR to BleScanStatus.ERROR
        )
        assertEquals(ScanPhase.entries.toSet(), mapping.keys)
        for ((phase, expected) in mapping) {
            assertEquals(expected, DiscoverySnapshot(scan = ScanState(phase)).toUiState(MainScreenUiState()).scanStatus)
        }
    }

    @Test fun snapshotProjectsDebugDetailsWithoutChangingControlsOrExposingStoredBytes() {
        val observation = ScanObservation("A", "peer", -20, null, emptyList(), mapOf(7 to listOf<Byte>(1, 2)), false)
        val snapshot = DiscoverySnapshot(
            scan = ScanState(ScanPhase.ERROR, "registration", 2),
            devices = listOf(DiscoveredDevice(observation, 10, 20, 3)), startedAtMillis = 5
        )
        val current = MainScreenUiState(showAllDevices = true, isBluetoothEnabled = true, hasRequiredPermissions = true)
        val ui = snapshot.toUiState(current)
        assertTrue(ui.showAllDevices)
        assertTrue(ui.hasRequiredPermissions)
        assertTrue(ui.isBluetoothEnabled)
        assertEquals("registration", ui.errorMessage)
        assertEquals(5L, ui.scanStartedAtMillis)
        assertEquals(1, ui.discoveredDeviceCount)
        assertEquals(3, ui.devices.single().scanCount)
        ui.devices.single().manufacturerData.getValue(7)[0] = 9
        assertEquals(listOf<Byte>(1, 2), snapshot.devices.single().observation.manufacturerData[7])
    }
}
