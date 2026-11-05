package com.example.scratched.meshMVP.transport

/** Session-local bounded cache, called under ScanSessionController's lock. */
internal class DiscoveryDeviceStore(private val maxDevices: Int = 256) {
    init { require(maxDevices > 0) }
    private val devices = LinkedHashMap<String, DiscoveredDevice>()

    fun clear() = devices.clear()

    fun record(observation: ScanObservation, time: Long): List<DiscoveredDevice> {
        val old = devices.remove(observation.address)
        val copy = observation.copy(
            name = observation.name?.takeIf { it.isNotBlank() } ?: old?.observation?.name,
            serviceUuids = observation.serviceUuids.toList(),
            manufacturerData = observation.manufacturerData.mapValues { it.value.toList() }
        )
        devices[copy.address] = DiscoveredDevice(
            observation = copy,
            firstSeenAtMillis = old?.firstSeenAtMillis ?: time,
            lastSeenAtMillis = time,
            scanCount = old?.scanCount?.let { if (it < Int.MAX_VALUE) it + 1 else it } ?: 1
        )
        if (devices.size > maxDevices) devices.remove(devices.keys.first())
        // Stable ordering for the debug list; access ordering is only used for eviction.
        return devices.values.sortedWith(compareBy({ it.firstSeenAtMillis }, { it.observation.address }))
    }
}
