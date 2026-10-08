package com.example.scratched.mesh


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings

/**
 * Interface for checking the status of geolocation services. that allows to achieve better
 * testability since separating Android-specific code
 */

interface LocationAdapterWrapper {
    fun isEnabled(): Boolean
    fun getEnableIntent(): Intent
}

/**
 * Implementation of [LocationAdapterWrapper] that contains Android specific code
 * @property context
 */

class UsableLocationAdapter(private val context: Context) : LocationAdapterWrapper {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("ObsoleteSdkInt")
    override fun isEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // (API 28+)
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    override fun getEnableIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
}