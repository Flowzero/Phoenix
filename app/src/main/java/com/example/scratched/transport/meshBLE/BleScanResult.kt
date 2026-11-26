package com.example.scratched.transport.meshBLE

import com.example.scratched.user.UserProfile

data class BleScanResult(
    val address: String,
    val profile: UserProfile,
    val observedAtMillis: Long
)