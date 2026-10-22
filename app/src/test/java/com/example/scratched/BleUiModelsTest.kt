package com.example.scratched

import com.example.scratched.support.device
import org.junit.Assert.*
import org.junit.Test

class BleUiModelsTest {
    @Test fun displayName_usesFallbackForMissingOrBlankNames() {
        for (name in listOf(null, "", " ", "\t\n")) {
            assertEquals("name=$name", "Unknown device", device(name = name).displayName)
        }
    }
    @Test fun displayName_keepsAdvertisedName() {
        assertEquals("PHOENIX", device(name = "PHOENIX").displayName)
    }
    @Test fun initialScreen_hasNoScanOrResults() {
        val state = MainScreenUiState()
        assertEquals(BleScanStatus.IDLE, state.scanStatus)
        assertTrue(state.devices.isEmpty())
        assertEquals(0, state.discoveredDeviceCount)
        assertNull(state.scanStartedAtMillis)
        assertNull(state.errorMessage)
    }
}
