package com.example.scratched.onboarding

import android.content.Context
import android.util.Log

/**
 * Implementation of [OnboardingStatusRepository] interface.
 * The data is stored in SharedPreferences
 *
 * @property context
 */

class OnboardingPrefsRepository(private val context: Context) : OnboardingStatusRepository {

    companion object {
        private const val TAG = "OnboardingPrefsRepository"
        private const val PREFS_NAME = "scratch_onboarding"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }

    private val sharedPrefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isFirstTimeLaunch(): Boolean {
        val isComplete = sharedPrefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val isFirstTime = !isComplete

        Log.d(TAG, "isComplete = $isComplete")
        Log.d(TAG, "isFirstTimeLaunch = $isFirstTime")
        return isFirstTime
    }

    override fun markComplete() {
        sharedPrefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()

        Log.d(TAG, "Onboarding marked as complete")
    }

    override fun resetCompletion() {
        sharedPrefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, false)
            .apply()

        Log.d(TAG, "Reset onboarding completion")
    }
}