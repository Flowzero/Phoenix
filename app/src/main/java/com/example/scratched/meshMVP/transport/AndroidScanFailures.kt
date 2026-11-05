package com.example.scratched.meshMVP.transport

/** Android's public ScanCallback codes. Kept free of Android runtime for regression tests. */
internal object AndroidScanFailures {
    fun fromCode(code: Int): ScanFailure {
        val reason = when (code) {
            1 -> "Scan already started"
            2 -> "Scanner registration failed"
            3 -> "Internal scanner error"
            4 -> "Scan feature is not supported"
            5 -> "No scanner hardware resources available"
            6 -> "Scanning too frequently; wait before retrying"
            else -> "Unknown scanner failure"
        }
        return ScanFailure(
            phase = if (code == 4) ScanPhase.UNSUPPORTED else ScanPhase.ERROR,
            message = "$reason (Android code $code)",
            androidErrorCode = code
        )
    }
}
