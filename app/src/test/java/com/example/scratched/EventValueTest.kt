package com.example.scratched

import com.example.scratched.onboarding.OnboardingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EventValueTest {

    @Test
    fun onboardingRequestToRequestPermissions_comparesArraysByContent() {
        val first = OnboardingEvent.RequestToRequestPermissions(arrayOf("one", "two"))
        val second = OnboardingEvent.RequestToRequestPermissions(arrayOf("one", "two"))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun onboardingRequestToRequestPermissions_detectsDifferentArrays() {
        val first = OnboardingEvent.RequestToRequestPermissions(arrayOf("one"))
        val second = OnboardingEvent.RequestToRequestPermissions(arrayOf("two"))

        assertNotEquals(first, second)
    }

    @Test
    fun activityRequestPermissions_comparesArraysByContent() {
        val first = ActivityEvent.RequestPermissions(arrayOf("one", "two"))
        val second = ActivityEvent.RequestPermissions(arrayOf("one", "two"))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun activityRequestPermissions_detectsDifferentArrays() {
        val first = ActivityEvent.RequestPermissions(arrayOf("one"))
        val second = ActivityEvent.RequestPermissions(arrayOf("two"))

        assertNotEquals(first, second)
    }
}
