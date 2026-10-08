package com.example.scratched.onboarding


import android.util.Log
import android.content.Context

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

        Log.d(TAG, "isComplete = $isComplete\nisFirstTimeLaunch = $isFirstTime")

        return isFirstTime
    }

    override fun isCompleted(): Boolean {
        val isComplete = sharedPrefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        Log.d(TAG, "isCompleted = $isComplete")

        return isComplete
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