package com.example.scratched.meshMVP.transport

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AndroidScanClock : ScanClock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
    override fun wallMillis(): Long = System.currentTimeMillis()
}

/** Owns only delay jobs; stopping a scan cancels its pending timer, close cancels all jobs. */
class CoroutineScanScheduler : ScanScheduler {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
        val job = scope.launch { delay(delayMillis); action() }
        return { job.cancel() }
    }
    override fun close() = scope.cancel()
}
