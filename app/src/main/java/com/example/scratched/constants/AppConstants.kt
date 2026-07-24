package com.example.scratched.constants
import java.util.UUID

object AppConstants {
    object DEBUG {
        val ONBOARDING_RESET = true
    }

    object GATT {
        val SERVICE_UUID: UUID       = UUID.fromString("b113843f-2034-4016-ae93-0ee9de50677d")
        val CHARACTERISTIC_UUID: UUID= UUID.fromString("a306a7f9-29fb-4687-8bb5-ffbaf3391d26")

        // DO NOT regenerate this one!
        val DESCRIPTOR_UUID: UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}