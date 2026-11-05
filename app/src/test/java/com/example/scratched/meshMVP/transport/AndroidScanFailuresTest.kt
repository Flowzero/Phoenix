package com.example.scratched.meshMVP.transport

import org.junit.Assert.*
import org.junit.Test

class AndroidScanFailuresTest {
    @Test fun publicAndUnknownCodesRetainNumericDiagnostic() {
        for (code in listOf(1, 2, 3, 4, 5, 6, 999)) {
            val failure = AndroidScanFailures.fromCode(code)
            assertEquals(code, failure.androidErrorCode)
            assertTrue(failure.message.contains("Android code $code"))
            assertEquals(if (code == 4) ScanPhase.UNSUPPORTED else ScanPhase.ERROR, failure.phase)
        }
    }
}
