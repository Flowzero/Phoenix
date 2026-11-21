package com.example.scratched.user

/**
 * Repository interface responsible for storing user's profile.
 * It abstracts the underlying storage mechanism (e.g. SharedPreferences, DataStore) from
 * the business logic,allowing the
 * [com.example.scratched.transport.meshBLE.BleAdvertiser]
 * to operate
 *
 * without knowing how the data is stored.
 */

interface UserProfileRepository {
    val currentProfile: UserProfile
    fun updateDisplayName(newName: String)
}