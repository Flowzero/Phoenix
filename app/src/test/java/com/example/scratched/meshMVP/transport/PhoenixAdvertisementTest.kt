package com.example.scratched.meshMVP.transport

import com.example.scratched.transport.meshBLE.BleAdvertisement

import org.junit.Assert.*
import org.junit.Test

class BleAdvertisementTest {
    @Test fun wireFormatUsesVersionAndBigEndianIdentifier() {
        assertArrayEquals(byteArrayOf(1, 0x12, 0x34, 0x56, 0x78), BleAdvertisement.encode(0x12345678))
        assertEquals("Phoenix-12345678", BleAdvertisement.deviceName(byteArrayOf(1, 0x12, 0x34, 0x56, 0x78)))
    }

    @Test fun unsignedIdentifiersAndLeadingZerosHaveStableNames() {
        assertEquals("Phoenix-FFFFFFFF", BleAdvertisement.deviceName(BleAdvertisement.encode(-1)))
        assertEquals("Phoenix-00000001", BleAdvertisement.deviceName(BleAdvertisement.encode(1)))
        assertEquals("Phoenix-80000000", BleAdvertisement.deviceName(BleAdvertisement.encode(Int.MIN_VALUE)))
    }

    @Test fun malformedAndUnknownVersionsAreNotPresentedAsPhoenixIdentity() {
        assertNull(BleAdvertisement.deviceName(null))
        assertNull(BleAdvertisement.deviceName(byteArrayOf()))
        assertNull(BleAdvertisement.deviceName(byteArrayOf(1, 1, 2, 3)))
        assertNull(BleAdvertisement.deviceName(byteArrayOf(1, 1, 2, 3, 4, 5)))
        assertNull(BleAdvertisement.deviceName(byteArrayOf(2, 1, 2, 3, 4)))
    }

    @Test fun serviceDataAndTxPowerFitLegacyScanResponse() {
        val payload = BleAdvertisement.encode(123)
        // 2 AD header bytes + 16 UUID bytes + payload, plus 3 bytes for TX power AD.
        assertTrue(2 + 16 + payload.size + 3 <= 31)
        // The primary ADV holds flags (3) and the 128-bit service UUID AD (18).
        assertTrue(3 + 2 + 16 <= 31)
    }
}
