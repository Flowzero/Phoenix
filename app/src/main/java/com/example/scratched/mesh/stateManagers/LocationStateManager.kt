package com.example.scratched.mesh.stateManagers

import android.content.Intent
import android.util.Log
import com.example.scratched.mesh.LocationAdapterWrapper
import com.example.scratched.mesh.LocationDisabledException

/**
 * Checks Location status (enabled/disabled/not_supported)
 */

class LocationStateManager(
    private val locationAdapter: LocationAdapterWrapper
) {
    companion object {
        const val TAG = "LocationStateManager"
    }

    fun ensureLocationEnabled(): Result<Unit> {
        return if (locationAdapter.isEnabled()) {
            Log.d(TAG, "Location services are enabled")
            Result.success(Unit)
        } else {
            Log.w(TAG, "Location services are DISABLED")
            Result.failure(LocationDisabledException())
        }
    }

    fun getEnableLocationIntent(): Intent? {
        return locationAdapter.getEnableIntent().also { intent ->
            if (false) {
                Log.d(BluetoothStateManager.Companion.TAG, "Cannot provide Enable Intent: missing permissions or adapter is unavailable")
            }
        }
    }

    fun isEnabled(): Boolean {
        return locationAdapter.isEnabled()
    }
}