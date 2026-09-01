package com.example.scratched.onboarding

/**
 * Repository interface responsible for storing onboarding state
 * It abstracts the underlying storage mechanism (e.g. SharedPreferences, DataStore) from
 * the business logic, allowing the [OnboardingFlowCoordinator] to check and update the
 * onboarding state without knowing how data is saved.
 */

interface OnboardingStatusRepository {
    /**
     * Checks if the user has ever completed the initial onboarding flow.
     *
     * @return `true` if this is the first time the app is launched and onboarding has
     *      not been completed yet; `false` otherwise.
     */
    fun isFirstTimeLaunch(): Boolean

    /**
     * Marks the onboarding process as successfully completed.
     *
     * This should be called when the user finishes all required steps.
     * Subsequent calls to [isFirstTimeLaunch] will return `false`, allowing
     * the app to bypass the onboarding flow and go straight to the main screen.
     */
    fun markComplete()

    /**
     * Resets the onboarding completion status back to its initial, uncompleted status.
     * This is primarily used for debugging, automated testing, or providing a "Show
     * onboarding again" feature in the app's settings.
     */
    fun resetCompletion()
}