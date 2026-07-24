package com.example.scratched.onboarding

interface OnboardingStateRepository {
    /**
     * true, if user jas never completed first-time onboarding
     */
    fun isFirstTimeLaunch(): Boolean

    /**
     *
     */
    fun markComplete()

    /**
     *
     */
    fun resetCompletion()
}