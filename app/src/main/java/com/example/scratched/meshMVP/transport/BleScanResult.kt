package com.example.scratched.meshMVP.transport

data class BleScanResult(
    val address: String,
    val profile: NearbyProfile,
    val observedAtMillis: Long
)
