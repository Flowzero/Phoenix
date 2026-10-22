package com.example.scratched.utilities

import android.content.Intent
import com.example.scratched.support.AndroidLogRule
import com.example.scratched.utilities.location.LocationDisabledException
import com.example.scratched.utilities.stateManagers.LocationAdapterWrapper
import com.example.scratched.utilities.stateManagers.LocationStateManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocationStateManagerTest {
    @get:Rule val logs = AndroidLogRule()
    private val adapter = mockk<LocationAdapterWrapper>()
    private val manager = LocationStateManager(adapter)

    @Test fun readiness_succeedsWhenEnabled() {
        every { adapter.isEnabled() } returns true
        assertTrue(manager.ensureLocationEnabled().isSuccess)
    }
    @Test fun readiness_returnsDomainErrorWhenDisabled() {
        every { adapter.isEnabled() } returns false
        assertTrue(manager.ensureLocationEnabled().exceptionOrNull() is LocationDisabledException)
    }
    @Test fun isEnabled_readsCurrentAdapterState() {
        every { adapter.isEnabled() } returnsMany listOf(false, true)
        assertFalse(manager.isEnabled())
        assertTrue(manager.isEnabled())
        verify(exactly = 2) { adapter.isEnabled() }
    }
    @Test fun enableIntent_isProvidedByAdapter() {
        val intent = mockk<Intent>()
        every { adapter.getEnableIntent() } returns intent
        assertSame(intent, manager.getEnableLocationIntent())
    }
}
