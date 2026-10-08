package com.example.scratched.mesh

/**
 *  Final Location state: DISABLED, ENABLED and NOT_SUPPORTED
 */

sealed class LocationState {
    object DISABLED: LocationState()
    object ENABLED: LocationState()
    object NOT_SUPPORTED: LocationState()
}

class LocationDisabledException: Exception("Location adapter is disabled. User needs to enable it")
class LocationNotSupported: Exception("Location is not supported on this device")