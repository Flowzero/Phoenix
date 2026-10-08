package com.example.scratched.onboarding

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnboardingPrefsRepositoryTest {

    private lateinit var context: Context
    private lateinit var applicationContext: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repository: OnboardingPrefsRepository
    private var onboardingComplete = false

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0

        context = mockk()
        applicationContext = mockk()
        sharedPreferences = mockk()
        editor = mockk()
        onboardingComplete = false

        every { context.applicationContext } returns applicationContext
        every {
            applicationContext.getSharedPreferences(
                "scratch_onboarding",
                Context.MODE_PRIVATE
            )
        } returns sharedPreferences
        every {
            sharedPreferences.getBoolean("onboarding_complete", false)
        } answers { onboardingComplete }
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean("onboarding_complete", any()) } answers {
            onboardingComplete = secondArg<Boolean>()
            editor
        }
        every { editor.apply() } just Runs

        repository = OnboardingPrefsRepository(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun isFirstTimeLaunch_returnsTrue_whenCompletionWasNotSaved() {
        assertTrue(repository.isFirstTimeLaunch())
    }

    @Test
    fun isFirstTimeLaunch_returnsFalse_whenCompletionWasSaved() {
        onboardingComplete = true

        assertFalse(repository.isFirstTimeLaunch())
    }

    @Test
    fun markComplete_savesCompletedState() {
        repository.markComplete()

        assertFalse(repository.isFirstTimeLaunch())
        verify(exactly = 1) {
            editor.putBoolean("onboarding_complete", true)
            editor.apply()
        }
    }

    @Test
    fun resetCompletion_restoresFirstLaunchState() {
        onboardingComplete = true

        repository.resetCompletion()

        assertTrue(repository.isFirstTimeLaunch())
        verify(exactly = 1) {
            editor.putBoolean("onboarding_complete", false)
            editor.apply()
        }
    }
}
