package com.example.scratched.meshMVP.transport

import org.junit.Assert.*
import org.junit.Test

class BluetoothGattClientManagerTest {
    private class Clock : ScanClock {
        var elapsed = 0L
        var wall = 1000L
        override fun elapsedMillis() = elapsed
        override fun wallMillis() = wall
    }
    private class Scheduler : ScanScheduler {
        var closed = 0
        var cancelled = 0
        val tasks = mutableListOf<() -> Unit>()
        override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
            assertTrue(delayMillis > 0)
            tasks.add(action)
            return { cancelled++ }
        }
        override fun close() { closed++ }
    }
    private class Scanner : BleScanner {
        val listeners = mutableListOf<BleScanner.Listener>()
        val filters = mutableListOf<DiscoveryFilter>()
        var stops = 0
        var startAction: ((BleScanner.Listener) -> Unit)? = null
        var stopFailure: Exception? = null
        override fun start(filter: DiscoveryFilter, listener: BleScanner.Listener): ScanHandle {
            filters.add(filter)
            listeners.add(listener)
            startAction?.invoke(listener)
            return ScanHandle { stops++; stopFailure?.let { throw it } }
        }
    }
    private class Fixture {
        val scanner = Scanner()
        val clock = Clock()
        val scheduler = Scheduler()
        var unavailable: ScanFailure? = null
        val manager = BluetoothGattClientManager(scanner, ScanReadiness { unavailable }, scheduler, clock)
        fun report(address: String = "A", name: String? = "peer", rssi: Int = -50) {
            scanner.listeners.last().onObservation(observation(address, name, rssi))
        }
        fun nextStart() { clock.elapsed += 6500; manager.startScanning() }
    }

    @Test fun resultsAreRetainedWithoutSubscriberAndMergedBeforeUiSeesThem() {
        val f = Fixture()
        f.manager.startScanning()
        f.report()
        f.clock.wall = 2000
        f.report(name = "new name", rssi = -20)
        val snapshot = f.manager.state.value
        assertEquals(ScanPhase.SCANNING, snapshot.scan.phase)
        assertEquals(1000L, snapshot.startedAtMillis)
        val device = snapshot.devices.single()
        assertEquals("new name", device.observation.name)
        assertEquals(-20, device.observation.rssi)
        assertEquals(2, device.scanCount)
        assertEquals(1000L, device.firstSeenAtMillis)
        assertEquals(2000L, device.lastSeenAtMillis)
    }

    @Test fun stopDiscardsLateResultsAndErrorsAndKeepsLastDebugList() {
        val f = Fixture()
        f.manager.startScanning()
        f.report()
        f.manager.stopScanning()
        f.report("late")
        f.scanner.listeners.last().onFailure(AndroidScanFailures.fromCode(2))
        assertEquals(ScanPhase.STOPPED, f.manager.state.value.scan.phase)
        assertEquals("A", f.manager.state.value.devices.single().observation.address)
        assertEquals(1, f.scanner.stops)
    }

    @Test fun newSessionClearsOldResultsAndRejectsOldCallback() {
        val f = Fixture()
        f.manager.startScanning()
        f.report()
        val old = f.scanner.listeners.last()
        f.manager.stopScanning()
        f.nextStart()
        old.onObservation(observation("old"))
        old.onFailure(AndroidScanFailures.fromCode(3))
        assertTrue(f.manager.state.value.devices.isEmpty())
        assertEquals(ScanPhase.SCANNING, f.manager.state.value.scan.phase)
    }

    @Test fun repeatedStartDoesNotClearDevicesOrChangeActiveFilter() {
        val f = Fixture()
        f.manager.startScanning()
        f.report()
        f.manager.startScanning(DiscoveryFilter.ALL_DEVICES)
        assertEquals(1, f.scanner.listeners.size)
        assertEquals(DiscoveryFilter.PHOENIX_ONLY, f.manager.state.value.filter)
        assertEquals(1, f.manager.state.value.devices.size)
    }

    @Test fun debugAllDevicesModeIsForwardedToBackend() {
        val f = Fixture()
        f.manager.startScanning(DiscoveryFilter.ALL_DEVICES)
        f.report("unrelated", null)
        assertEquals(listOf(DiscoveryFilter.ALL_DEVICES), f.scanner.filters)
        assertEquals(DiscoveryFilter.ALL_DEVICES, f.manager.state.value.filter)
        assertEquals(1, f.manager.state.value.devices.size)
    }

    @Test fun stopCancelsDelayedStartAndStartTimeIsNotRequestTime() {
        val f = Fixture()
        f.manager.startScanning()
        f.manager.stopScanning()
        f.manager.startScanning()
        assertEquals(ScanPhase.WAITING, f.manager.state.value.scan.phase)
        assertNull(f.manager.state.value.startedAtMillis)
        f.manager.stopScanning()
        f.clock.elapsed = 6500
        f.scheduler.tasks.single().invoke() // Already queued timer may run despite cancellation.
        assertEquals(1, f.scanner.listeners.size)
        assertEquals(1, f.scheduler.cancelled)
    }

    @Test fun waitingStartRechecksReadinessBeforeTouchingRadio() {
        val f = Fixture()
        f.manager.startScanning()
        f.manager.stopScanning()
        f.manager.startScanning()
        f.unavailable = ScanFailure(ScanPhase.PERMISSION_REQUIRED, "revoked")
        f.clock.elapsed = 6500
        f.scheduler.tasks.single().invoke()
        assertEquals(1, f.scanner.listeners.size)
        assertEquals(ScanPhase.PERMISSION_REQUIRED, f.manager.state.value.scan.phase)
        assertNull(f.manager.state.value.startedAtMillis)
    }

    @Test fun failedReadinessDoesNotConsumeRadioQuota() {
        val f = Fixture()
        f.unavailable = ScanFailure(ScanPhase.LOCATION_DISABLED, "location off")
        f.manager.startScanning()
        assertTrue(f.scanner.listeners.isEmpty())
        f.unavailable = null
        f.manager.startScanning()
        assertEquals(ScanPhase.SCANNING, f.manager.state.value.scan.phase)
        assertTrue(f.scheduler.tasks.isEmpty())
    }

    @Test fun refreshReleasesActiveSessionWhenPermissionRevoked() {
        val f = Fixture()
        f.manager.startScanning()
        f.unavailable = ScanFailure(ScanPhase.PERMISSION_REQUIRED, "revoked")
        f.manager.refreshReadiness()
        assertEquals(1, f.scanner.stops)
        assertEquals(ScanPhase.PERMISSION_REQUIRED, f.manager.state.value.scan.phase)
    }

    @Test fun synchronousFailureBeforeHandleReturnedStillClosesThatHandle() {
        val f = Fixture()
        f.scanner.startAction = { it.onFailure(AndroidScanFailures.fromCode(2)) }
        f.manager.startScanning()
        assertEquals(1, f.scanner.stops)
        assertEquals(2, f.manager.state.value.scan.androidErrorCode)
        f.manager.stopScanning() // Screen going away must not erase the diagnostic.
        assertEquals(2, f.manager.state.value.scan.androidErrorCode)
    }

    @Test fun synchronousResultsBeforeHandleReturnedAreAccepted() {
        val f = Fixture()
        f.scanner.startAction = { it.onObservation(observation("sync")) }
        f.manager.startScanning()
        assertEquals("sync", f.manager.state.value.devices.single().observation.address)
    }

    @Test fun synchronousSecurityExceptionIsPermissionFailure() {
        val f = Fixture()
        f.scanner.startAction = { throw SecurityException("revoked") }
        f.manager.startScanning()
        assertEquals(ScanPhase.PERMISSION_REQUIRED, f.manager.state.value.scan.phase)
        assertNotNull(f.manager.state.value.scan.error)
    }

    @Test fun cleanupFailureDoesNotLeaveSessionActive() {
        val f = Fixture()
        f.manager.startScanning()
        f.scanner.stopFailure = IllegalArgumentException("invalid callback")
        f.manager.stopScanning()
        assertEquals(ScanPhase.ERROR, f.manager.state.value.scan.phase)
        f.report("late")
        assertTrue(f.manager.state.value.devices.isEmpty())
    }

    @Test fun originalPlatformErrorSurvivesCleanupFailure() {
        val f = Fixture()
        f.manager.startScanning()
        f.scanner.stopFailure = IllegalArgumentException("cleanup")
        f.scanner.listeners.last().onFailure(AndroidScanFailures.fromCode(6))
        assertEquals(6, f.manager.state.value.scan.androidErrorCode)
    }

    @Test fun closeCancelsPendingTimerAndFutureStarts() {
        val f = Fixture()
        f.manager.startScanning()
        f.manager.stopScanning()
        f.manager.startScanning()
        f.manager.close()
        f.scheduler.tasks.single().invoke()
        f.manager.startScanning()
        assertEquals(1, f.scanner.listeners.size)
        assertEquals(1, f.scheduler.cancelled)
        assertEquals(1, f.scheduler.closed)
    }

    @Test fun closeIsIdempotentAndReleasesAnActiveHandleOnce() {
        val f = Fixture()
        f.manager.startScanning()
        f.manager.close()
        f.manager.close()
        f.manager.stopScanning()
        assertEquals(1, f.scanner.stops)
        assertEquals(1, f.scheduler.closed)
    }

    @Test fun closeDuringSynchronousStartReleasesReturnedHandle() {
        val f = Fixture()
        f.scanner.startAction = { f.manager.close() }
        f.manager.startScanning()
        assertEquals(1, f.scanner.stops)
        assertEquals(1, f.scheduler.closed)
        f.report("late")
        assertTrue(f.manager.state.value.devices.isEmpty())
    }

    @Test fun changingWallClockDoesNotBypassMonotonicQuota() {
        val f = Fixture()
        f.manager.startScanning()
        f.manager.stopScanning()
        f.clock.wall = Long.MAX_VALUE
        f.manager.startScanning()
        assertEquals(ScanPhase.WAITING, f.manager.state.value.scan.phase)
        f.clock.elapsed = 6500
        f.scheduler.tasks.single().invoke()
        assertEquals(2, f.scanner.listeners.size)
    }

    companion object {
        internal fun observation(address: String, name: String? = "peer", rssi: Int = -50) = ScanObservation(
            address, name, rssi, null, emptyList(), emptyMap(), false
        )
    }
}
