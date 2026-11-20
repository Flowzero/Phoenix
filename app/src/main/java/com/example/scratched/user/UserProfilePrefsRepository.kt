package com.example.scratched.user

import android.content.Context
import java.security.SecureRandom


/**
 * Implementation of [UserProfileRepository] interface.
 * The data is stored in SharedPreferences
 *
 * @property context
 */

class UserProfilePrefsRepository(context: Context) : UserProfileRepository {

    private companion object {
        const val PREFERENCES_FILE = "nearby_profile"
        const val PROFILE_ID_KEY = "id"
        const val DISPLAY_NAME_KEY = "name"
    }

    private val profilePreferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    override var currentProfile: UserProfile = loadOrCreateProfile()
        private set

    override fun updateDisplayName(newName: String) {
        require(UserProfile.validName(newName)) {
            "This name is unavailable: $newName"
        }

        profilePreferences.edit()
            .putString(DISPLAY_NAME_KEY, newName)
            .apply()

        currentProfile = currentProfile.copy(name = newName)
    }

    private fun loadOrCreateProfile(): UserProfile {
        val profileId = if (profilePreferences.contains(PROFILE_ID_KEY)) {
            profilePreferences.getInt(PROFILE_ID_KEY, 0)
        } else {
            val newId = SecureRandom().nextInt()

            profilePreferences.edit()
                .putInt(PROFILE_ID_KEY, newId)
                .apply()

            newId
        }

        val savedName = profilePreferences.getString(DISPLAY_NAME_KEY, null)

        return if (savedName != null && UserProfile.validName(savedName)) {
            UserProfile(id = profileId, name = savedName)
        } else {
            UserProfile.initial(profileId)
        }
    }
}