package com.example.scratched.onboarding

/**
 * Repository interface responsible for storing onboarding state.
 * It abstracts the underlying storage mechanism (e.g. SharedPreferences, DataStore) from
 * the business logic, allowing the [OnboardingFlowCoordinator] to check and update the
 * onboarding state without knowing how the data is stored.
 */

interface OnboardingStatusRepository {
    fun isFirstTimeLaunch(): Boolean
    fun markComplete()
    fun resetCompletion()
}