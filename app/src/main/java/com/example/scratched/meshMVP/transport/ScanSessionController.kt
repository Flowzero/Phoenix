package com.example.scratched.meshMVP.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class ScanPhase { IDLE, WAITING, SCANNING, STOPPED, PERMISSION_REQUIRED, BLUETOOTH_DISABLED, ERROR }
internal data class ScanState(val phase: ScanPhase, val error: String? = null)
internal class ScanUnavailable(val phase: ScanPhase, message: String) : Exception(message)

/** Serializes requests and invalidates callbacks/timers belonging to a previous scan. */
internal class ScanSessionController(
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val startRadio: (Long) -> Unit,
    private val stopRadio: () -> Unit,
) {
    private val mutableState = MutableStateFlow(ScanState(ScanPhase.IDLE))
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var lastStart: Long? = null
    private var cancelPending: (() -> Unit)? = null
    private var closed = false

    @Synchronized fun start() {
        if (closed || state.value.phase in listOf(ScanPhase.WAITING, ScanPhase.SCANNING)) return
        val session = ++generation
        val wait = lastStart?.let { (6_500L - (now() - it)).coerceAtLeast(0L) } ?: 0L
        if (wait > 0L) {
            mutableState.value = ScanState(ScanPhase.WAITING)
            cancelPending = schedule(wait) { begin(session) }
        } else begin(session)
    }

    @Synchronized private fun begin(session: Long) {
        if (closed || session != generation) return
        cancelPending = null
        mutableState.value = ScanState(ScanPhase.SCANNING)
        // Count attempts, including failures, against Android's scan-start quota.
        lastStart = now()
        try {
            startRadio(session)
        } catch (e: ScanUnavailable) {
            fail(session, e.message ?: "Scan unavailable", e.phase)
        } catch (e: Exception) {
            fail(session, e.message ?: "Unable to start BLE scan")
        }
    }

    @Synchronized fun accepts(session: Long): Boolean =
        !closed && session == generation && state.value.phase == ScanPhase.SCANNING

    @Synchronized fun fail(session: Long, message: String, phase: ScanPhase = ScanPhase.ERROR) {
        if (!accepts(session)) return
        stop()
        mutableState.value = ScanState(phase, message)
    }

    @Synchronized fun stop() {
        ++generation
        cancelPending?.invoke()
        cancelPending = null
        // Always release local state, even after permissions have been revoked.
        try { stopRadio() } finally { mutableState.value = ScanState(ScanPhase.STOPPED) }
    }

    @Synchronized fun close() {
        closed = true
        stop()
    }
}
