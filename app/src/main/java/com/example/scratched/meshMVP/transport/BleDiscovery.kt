package com.example.scratched.meshMVP.transport

import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/** Android- and UI-independent discovery contract. No GATT connections are implied. */
interface BleDiscovery {
    val state: StateFlow<DiscoverySnapshot>
    fun startScanning(filter: DiscoveryFilter = DiscoveryFilter.PHOENIX_ONLY)
    fun stopScanning()
    fun refreshReadiness()
    fun close()
}

enum class DiscoveryFilter { PHOENIX_ONLY, ALL_DEVICES }
enum class ScanPhase {
    IDLE, WAITING, SCANNING, STOPPED, PERMISSION_REQUIRED, BLUETOOTH_DISABLED,
    LOCATION_DISABLED, UNSUPPORTED, ERROR;

    val isActive: Boolean get() = this == WAITING || this == SCANNING
}

data class ScanState(val phase: ScanPhase = ScanPhase.IDLE, val error: String? = null, val androidErrorCode: Int? = null)
data class DiscoverySnapshot(
    val scan: ScanState = ScanState(),
    val devices: List<DiscoveredDevice> = emptyList(),
    val startedAtMillis: Long? = null,
    val filter: DiscoveryFilter = DiscoveryFilter.PHOENIX_ONLY
)

data class ScanObservation(
    val address: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int?,
    val serviceUuids: List<UUID>,
    val manufacturerData: Map<Int, List<Byte>>,
    val isConnectable: Boolean?
)

data class DiscoveredDevice(
    val observation: ScanObservation,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val scanCount: Int
)

data class ScanFailure(val phase: ScanPhase, val message: String, val androidErrorCode: Int? = null)

fun interface ScanReadiness { fun failure(): ScanFailure? }
fun interface ScanHandle { fun stop() }

interface BleScanner {
    interface Listener {
        fun onObservation(observation: ScanObservation)
        fun onFailure(failure: ScanFailure)
    }
    /** May call listener synchronously, including before returning its handle. */
    fun start(filter: DiscoveryFilter, listener: Listener): ScanHandle
}

interface ScanClock {
    fun elapsedMillis(): Long
    fun wallMillis(): Long
}

interface ScanScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit
    fun close()
}
