package com.example.scratched.user

import com.example.scratched.meshMVP.transport.NearbyProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface responsible for storing user's profile.
 * It abstracts the underlying storage mechanism (e.g. SharedPreferences, DataStore) from
 * the business logic,allowing the
 * [com.example.scratched.transport.meshBLE.BluetoothGattServerManager]
 * to operate
 *
 * without knowing how the data is stored.
 */

interface UserProfileRepository {
    val currentProfile: UserProfile
    fun updateDisplayName(newName: String)
}