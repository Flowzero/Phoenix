package com.example.scratched.meshMVP.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.utilities.stateManagers.LocationStateManager

/** Checks discovery requirements only; ADVERTISE is not needed to receive advertisements. */
class AndroidScanReadiness(
    context: Context,
    private val permissions: PermissionsManager,
    private val location: LocationStateManager
) : ScanReadiness {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override fun failure(): ScanFailure? = try {
        when {
            !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ->
                ScanFailure(ScanPhase.UNSUPPORTED, "Bluetooth LE is not supported")
            !permissions.hasScanPermissions() ->
                ScanFailure(ScanPhase.PERMISSION_REQUIRED, "Bluetooth scan permissions are required")
            else -> {
                val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                when {
                    adapter == null -> ScanFailure(ScanPhase.UNSUPPORTED, "Bluetooth adapter is unavailable")
                    !adapter.isEnabled -> ScanFailure(ScanPhase.BLUETOOTH_DISABLED, "Bluetooth is disabled")
                    permissions.requiresLocationServices() && !location.isEnabled() ->
                        ScanFailure(ScanPhase.LOCATION_DISABLED, "Enable location services to scan on Android 10–11")
                    else -> null
                }
            }
        }
    } catch (e: SecurityException) {
        ScanFailure(ScanPhase.PERMISSION_REQUIRED, "Bluetooth scan permission was revoked")
    }
}
