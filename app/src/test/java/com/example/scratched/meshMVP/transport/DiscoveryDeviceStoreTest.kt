package com.example.scratched.meshMVP.transport

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DiscoveryDeviceStoreTest {
    @Test fun boundedCacheEvictsLeastRecentlyObservedDevice() {
        val store = DiscoveryDeviceStore(2)
        store.record(observation("A"), 1)
        store.record(observation("B"), 2)
        store.record(observation("A"), 3)
        val devices = store.record(observation("C"), 4)
        assertEquals(listOf("A", "C"), devices.map { it.observation.address })
        assertEquals(2, devices.first().scanCount)
    }

    @Test fun storesDefensiveCopiesAndDoesNotMutatePreviousSnapshot() {
        val store = DiscoveryDeviceStore()
        val bytes = mutableListOf<Byte>(1, 2)
        val uuids = mutableListOf(UUID.randomUUID())
        val data = mutableMapOf<Int, List<Byte>>(1 to bytes)
        val first = store.record(observation("A").copy(serviceUuids = uuids, manufacturerData = data), 1)
        bytes[0] = 9
        uuids.clear()
        data.clear()
        store.record(observation("A"), 2)
        assertEquals(listOf<Byte>(1, 2), first.single().observation.manufacturerData[1])
        assertEquals(1, first.single().observation.serviceUuids.size)
        assertEquals(1, first.single().scanCount)
    }

    @Test fun missingOrBlankNamePreservesLastKnownNameWhileMetadataRefreshes() {
        val store = DiscoveryDeviceStore()
        store.record(observation("A").copy(name = "known", txPower = 4), 1)
        val device = store.record(observation("A").copy(name = "  ", txPower = null), 2).single()
        assertEquals("known", device.observation.name)
        assertNull(device.observation.txPower)
        assertEquals(false, device.observation.isConnectable)
        assertEquals(1L, device.firstSeenAtMillis)
        assertEquals(2L, device.lastSeenAtMillis)
    }

    @Test fun clearStartsFreshCounterAndTimestamps() {
        val store = DiscoveryDeviceStore()
        store.record(observation("A"), 1)
        store.clear()
        val device = store.record(observation("A"), 10).single()
        assertEquals(1, device.scanCount)
        assertEquals(10L, device.firstSeenAtMillis)
    }

    private fun observation(address: String) = BluetoothGattClientManagerTest.observation(address)
}
