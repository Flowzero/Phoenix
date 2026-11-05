package com.example.scratched.meshMVP.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coordinates discovery only. Android API calls, UI mapping and device merging live elsewhere. */
class BluetoothGattClientManager(
    private val scanner: BleScanner,
    private val readiness: ScanReadiness,
    private val scheduler: ScanScheduler,
    private val clock: ScanClock,
) : BleDiscovery {
    private val mutableState = MutableStateFlow(DiscoverySnapshot())
    override val state = mutableState.asStateFlow()
    private val store = DiscoveryDeviceStore()
    private var handle: ScanHandle? = null
    private var requestedFilter = DiscoveryFilter.PHOENIX_ONLY
    private val controller = ScanSessionController(
        now = clock::elapsedMillis,
        schedule = scheduler::schedule,
        validateStart = { readiness.failure()?.let { throw ScanUnavailable(it) } },
        onNewSession = {
            store.clear()
            mutableState.value = DiscoverySnapshot(filter = requestedFilter)
        },
        onStateChanged = { scan ->
            val current = mutableState.value
            mutableState.value = current.copy(
                scan = scan,
                startedAtMillis = if (scan.phase == ScanPhase.SCANNING) clock.wallMillis() else current.startedAtMillis
            )
        },
        startRadio = ::startRadio,
        stopRadio = { val active = handle; handle = null; active?.stop() }
    )

    private fun startRadio(session: Long) {
        val opened = scanner.start(requestedFilter, object : BleScanner.Listener {
            override fun onObservation(observation: ScanObservation) {
                controller.withActiveSession(session) {
                    mutableState.value = mutableState.value.copy(devices = store.record(observation, clock.wallMillis()))
                }
            }
            override fun onFailure(failure: ScanFailure) { controller.fail(session, failure) }
        })
        // start() is allowed to synchronously report failure or trigger cancellation.
        if (controller.accepts(session)) handle = opened else opened.stop()
    }

    override fun startScanning(filter: DiscoveryFilter) {
        controller.start { requestedFilter = filter }
    }

    override fun stopScanning() = controller.stop()
    override fun refreshReadiness() = controller.refreshReadiness()
    override fun close() {
        if (controller.close()) scheduler.close()
    }
}
