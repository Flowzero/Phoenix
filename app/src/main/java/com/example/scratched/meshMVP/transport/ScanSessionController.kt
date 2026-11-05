package com.example.scratched.meshMVP.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ScanUnavailable(val failure: ScanFailure) : Exception(failure.message) {
    constructor(phase: ScanPhase, message: String) : this(ScanFailure(phase, message))
}

/** All callbacks and state updates use the same lock, including synchronous radio callbacks. */
internal class ScanSessionController(
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val startRadio: (Long) -> Unit,
    private val stopRadio: () -> Unit,
    private val validateStart: () -> Unit = {},
    private val onNewSession: () -> Unit = {},
    private val onStateChanged: (ScanState) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(ScanState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var lastStart: Long? = null
    private var cancelPending: (() -> Unit)? = null
    private var closed = false

    @Synchronized fun start(prepare: () -> Unit = {}) {
        if (closed || state.value.phase.isActive) return
        prepare()
        val session = ++generation
        mutableState.value = ScanState(ScanPhase.WAITING)
        onNewSession()
        if (closed || session != generation || state.value.phase != ScanPhase.WAITING) return
        onStateChanged(state.value)
        if (!validate(session)) return
        val wait = lastStart?.let { (6_500L - (now() - it)).coerceAtLeast(0L) } ?: 0L
        if (wait > 0L) {
            try { cancelPending = schedule(wait) { begin(session) } }
            catch (e: Exception) { fail(session, e.message ?: "Unable to schedule scan") }
        } else begin(session)
    }

    @Synchronized private fun begin(session: Long) {
        if (closed || session != generation || state.value.phase != ScanPhase.WAITING) return
        cancelPending = null
        if (!validate(session)) return
        setState(ScanState(ScanPhase.SCANNING))
        if (!accepts(session)) return
        lastStart = now()
        try { startRadio(session) }
        catch (e: ScanUnavailable) { fail(session, e.failure) }
        catch (e: SecurityException) { fail(session, "Scan permission was revoked", ScanPhase.PERMISSION_REQUIRED) }
        catch (e: Exception) { fail(session, e.message ?: "Unable to start BLE scan") }
    }

    private fun validate(session: Long): Boolean {
        return try { validateStart(); true }
        catch (e: ScanUnavailable) { fail(session, e.failure); false }
        catch (e: SecurityException) { fail(session, "Scan permission was revoked", ScanPhase.PERMISSION_REQUIRED); false }
        catch (e: Exception) { fail(session, e.message ?: "Unable to check scan readiness"); false }
    }

    @Synchronized fun accepts(session: Long): Boolean =
        !closed && session == generation && state.value.phase == ScanPhase.SCANNING

    @Synchronized fun withActiveSession(session: Long, action: () -> Unit) {
        if (accepts(session)) action()
    }

    @Synchronized fun fail(session: Long, message: String, phase: ScanPhase = ScanPhase.ERROR) =
        fail(session, ScanFailure(phase, message))

    @Synchronized fun fail(session: Long, failure: ScanFailure) {
        if (closed || session != generation || !state.value.phase.isActive) return
        invalidateAndRelease()
        // Preserve the original platform failure even if cleanup also failed.
        setState(ScanState(failure.phase, failure.message, failure.androidErrorCode))
    }

    @Synchronized fun refreshReadiness() {
        if (!closed && state.value.phase.isActive) validate(generation)
    }

    @Synchronized fun stop() {
        if (closed || !state.value.phase.isActive) return
        val cleanupFailure = invalidateAndRelease()
        setState(if (cleanupFailure == null) ScanState(ScanPhase.STOPPED)
            else ScanState(ScanPhase.ERROR, "Unable to stop scan: ${cleanupFailure.message}"))
    }

    @Synchronized fun close(): Boolean {
        if (closed) return false
        closed = true // Prevent a state observer from starting a session during cleanup.
        val wasActive = state.value.phase.isActive
        val cleanupFailure = invalidateAndRelease()
        if (wasActive) {
            setState(if (cleanupFailure == null) ScanState(ScanPhase.STOPPED)
                else ScanState(ScanPhase.ERROR, "Unable to stop scan: ${cleanupFailure.message}"))
        }
        return true
    }

    private fun invalidateAndRelease(): Exception? {
        ++generation
        val cancel = cancelPending
        cancelPending = null
        var failure: Exception? = null
        try { cancel?.invoke() } catch (e: Exception) { failure = e }
        try { stopRadio() } catch (e: Exception) { failure = e }
        return failure
    }

    private fun setState(value: ScanState) {
        mutableState.value = value
        onStateChanged(value)
    }
}
