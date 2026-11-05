package com.example.scratched.meshMVP.transport

import org.junit.Assert.*
import org.junit.Test

class ScanSessionControllerTest {
    private class Fixture {
        var time = 0L
        var stops = 0
        val starts = mutableListOf<Long>()
        val timers = mutableListOf<() -> Unit>()
        var cancelled = false
        var failure: Exception? = null
        val controller = ScanSessionController(
            now = { time },
            schedule = { _, action -> timers.add(action); { cancelled = true } },
            startRadio = { starts.add(it); failure?.let { e -> throw e } },
            stopRadio = { stops++ }
        )
    }

    @Test fun firstStartWorksAtMonotonicZeroAndRepeatedStartIsIdempotent() {
        val f = Fixture()
        f.controller.start()
        f.controller.start()
        assertEquals(1, f.starts.size)
        assertEquals(ScanPhase.SCANNING, f.controller.state.value.phase)
        assertTrue(f.controller.accepts(f.starts.single()))
    }

    @Test fun stopCancelsPendingRestartEvenIfTimerAlreadyQueued() {
        val f = Fixture()
        f.controller.start()
        f.controller.stop()
        f.time = 100
        f.controller.start()
        assertEquals(ScanPhase.WAITING, f.controller.state.value.phase)
        f.controller.start()
        assertEquals(1, f.timers.size)
        f.controller.stop()
        f.time = 6500
        f.timers.single().invoke()
        assertTrue(f.cancelled)
        assertEquals(1, f.starts.size)
        assertEquals(ScanPhase.STOPPED, f.controller.state.value.phase)
    }

    @Test fun restartRejectsOldResultsAndOldFailure() {
        val f = Fixture()
        f.controller.start()
        val old = f.starts.single()
        f.controller.stop()
        f.time = 6500
        f.controller.start()
        assertFalse(f.controller.accepts(old))
        f.controller.fail(old, "late failure")
        assertEquals(ScanPhase.SCANNING, f.controller.state.value.phase)
        assertTrue(f.controller.accepts(f.starts.last()))
    }

    @Test fun asynchronousFailureReleasesRadioAndAllowsRestart() {
        val f = Fixture()
        f.controller.start()
        f.controller.fail(f.starts.single(), "Android code 2")
        assertEquals(1, f.stops)
        assertEquals(ScanState(ScanPhase.ERROR, "Android code 2"), f.controller.state.value)
        f.time = 6500
        f.controller.start()
        assertEquals(2, f.starts.size)
    }

    @Test fun synchronousFailureDoesNotLeaveScanningState() {
        val f = Fixture()
        f.failure = SecurityException("revoked")
        f.controller.start()
        assertEquals(ScanState(ScanPhase.ERROR, "revoked"), f.controller.state.value)
        assertFalse(f.controller.accepts(f.starts.single()))
        assertEquals(1, f.stops)
    }

    @Test fun delayedStartRechecksReadinessAndReportsPermissionRevocation() {
        val f = Fixture()
        f.controller.start()
        f.controller.stop()
        f.controller.start()
        f.failure = ScanUnavailable(ScanPhase.PERMISSION_REQUIRED, "revoked")
        f.time = 6500
        f.timers.single().invoke()
        assertEquals(ScanPhase.PERMISSION_REQUIRED, f.controller.state.value.phase)
    }

    @Test fun closeInvalidatesCallbacksAndPreventsFutureStarts() {
        val f = Fixture()
        f.controller.start()
        f.controller.stop()
        f.controller.start()
        f.controller.close()
        f.time = 6500
        f.timers.single().invoke()
        f.controller.start()
        assertEquals(1, f.starts.size)
        assertEquals(ScanPhase.STOPPED, f.controller.state.value.phase)
    }

    @Test fun pendingRestartStartsOnceWithFreshGeneration() {
        val f = Fixture()
        f.controller.start()
        val old = f.starts.single()
        f.controller.stop()
        f.time = 100
        f.controller.start()
        f.time = 6500
        f.timers.single().invoke()
        assertEquals(2, f.starts.size)
        assertFalse(f.controller.accepts(old))
        assertTrue(f.controller.accepts(f.starts.last()))
    }
}
