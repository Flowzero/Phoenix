package com.example.scratched.utilities

import android.content.Context
import android.location.LocationManager
import android.provider.Settings
import com.example.scratched.utilities.stateManagers.UsableLocationAdapter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class UsableLocationAdapterTest {
    private val location = mockk<LocationManager>()
    private val context = mockk<Context> {
        every { getSystemService(Context.LOCATION_SERVICE) } returns location
    }
    private val adapter = UsableLocationAdapter(context)

    @Test fun enabled_reflectsSystemSetting() {
        every { location.isLocationEnabled } returnsMany listOf(false, true)
        assertFalse(adapter.isEnabled())
        assertTrue(adapter.isEnabled())
    }
    @Test fun enableIntent_opensLocationSettings() {
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, adapter.getEnableIntent().action)
    }
}
