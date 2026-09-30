package com.example.scratched.onboarding

import android.content.Context


class OnboardingPrefsRepository(private val context: Context) : OnboardingStatusRepository {

    companion object {
        private const val TAG = "OnboardingPrefsRepository"
        private const val PREFS_NAME = "scratch_onboarding"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
    private val sharedPrefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isFirstTimeLaunch(): Boolean {
        return !sharedPrefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    override fun markComplete() {
        sharedPrefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    override fun resetCompletion() {
        sharedPrefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, false)
            .apply()
    }
}