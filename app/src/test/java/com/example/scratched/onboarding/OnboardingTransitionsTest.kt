package com.example.scratched.onboarding

import android.Manifest
import android.os.Build
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.utilities.stateManagers.BluetoothStateManager
import com.example.scratched.utilities.stateManagers.LocationStateManager
import io.mockk.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 35])
class OnboardingTransitionsTest {
    private val btPermission = Manifest.permission.BLUETOOTH_SCAN
    private val locPermission = Manifest.permission.ACCESS_FINE_LOCATION
    private val notification = Manifest.permission.POST_NOTIFICATIONS
    private val granted = mutableSetOf(btPermission, locPermission, notification)
    private var bluetoothEnabled = true
    private var locationEnabled = true
    private var completed = false
    private var completionWrites = 0
    private val repository = object : OnboardingStatusRepository {
        override fun isFirstTimeLaunch() = !completed
        override fun isCompleted() = completed
        override fun markComplete() { completed = true; completionWrites++ }
        override fun resetCompletion() { completed = false }
    }
    private val permissions = mockk<PermissionsManager> {
        every { getBluetoothPermissions() } returns listOf(btPermission)
        every { getLocationPermissions() } returns listOf(locPermission)
        every { getNotificationPermissions() } returns listOf(notification)
        every { getRequiredPermissions() } returns listOf(btPermission, locPermission)
        every { getMissingRequiredPermissions() } answers { listOf(btPermission, locPermission) - granted }
        every { isPermissionGranted(any()) } answers { firstArg<String>() in granted }
    }
    private val bluetooth = mockk<BluetoothStateManager> {
        every { isEnabled() } answers { bluetoothEnabled }
    }
    private val location = mockk<LocationStateManager> {
        every { isEnabled() } answers { locationEnabled }
    }
    private val flow = OnboardingFlowCoordinator(permissions, bluetooth, location, repository)

    private fun reachRequirements() {
        flow.startAppFlow()
        flow.navigateToNextStep()
        flow.navigateToNextStep()
    }

    @Test fun requirements_areRequestedInPriorityOrder() {
        granted.clear()
        bluetoothEnabled = false
        locationEnabled = false
        reachRequirements()
        assertEquals(OnboardingState.BLUETOOTH, flow.currentState.value)
        granted += btPermission
        flow.navigateToNextStep()
        assertEquals(OnboardingState.ENABLE_BLUETOOTH, flow.currentState.value)
        bluetoothEnabled = true
        flow.navigateToNextStep()
        assertEquals(OnboardingState.LOCATION, flow.currentState.value)
        granted += locPermission
        flow.navigateToNextStep()
        assertEquals(OnboardingState.ENABLE_LOCATION, flow.currentState.value)
        locationEnabled = true
        flow.navigateToNextStep()
        val expected = if (Build.VERSION.SDK_INT >= 33) OnboardingState.NOTIFICATION else OnboardingState.ALL_SET
        assertEquals(expected, flow.currentState.value)
        if (Build.VERSION.SDK_INT >= 33) flow.onHandlePermissionsResult(mapOf(notification to false))
        assertEquals(OnboardingState.ALL_SET, flow.currentState.value)
        flow.navigateToNextStep()
        assertTrue(completed)
        assertEquals(OnboardingState.COMPLETED, flow.currentState.value)
    }
    @Test fun enabledLocation_continuesWithoutRepeatingCompletedIntro() {
        completed = true
        granted -= locPermission
        flow.startAppFlow()
        assertEquals(OnboardingState.LOCATION, flow.currentState.value)
        granted += locPermission
        flow.navigateToNextStep()
        assertEquals(OnboardingState.COMPLETED, flow.currentState.value)
    }
    @Test fun disabledLocation_keepsEnableStepUntilSettingChanges() {
        locationEnabled = false
        reachRequirements()
        assertEquals(OnboardingState.ENABLE_LOCATION, flow.currentState.value)
        flow.navigateToNextStep()
        assertEquals(OnboardingState.ENABLE_LOCATION, flow.currentState.value)
        locationEnabled = true
        flow.navigateToNextStep()
        assertEquals(OnboardingState.ALL_SET, flow.currentState.value)
    }
    @Test fun completion_isStoredOnceWhenNoRequirementChanges() {
        reachRequirements()
        flow.navigateToNextStep()
        flow.navigateToNextStep()
        assertEquals(1, completionWrites)
        assertEquals(OnboardingState.COMPLETED, flow.currentState.value)
    }
    @Test fun completedFlow_canReturnToBluetoothRequirement() {
        completed = true
        flow.startAppFlow()
        bluetoothEnabled = false
        flow.navigateToNextStep()
        assertEquals(OnboardingState.ENABLE_BLUETOOTH, flow.currentState.value)
    }
    @Test fun reset_restartsFromWelcome() {
        completed = true
        flow.startAppFlow()
        flow.resetOnboarding()
        flow.startAppFlow()
        assertFalse(completed)
        assertEquals(OnboardingState.WELCOME, flow.currentState.value)
    }
    @Test fun locationRequest_containsOnlyDeniedPermissions() = runTest {
        every { permissions.getLocationPermissions() } returns listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION, locPermission
        )
        granted += Manifest.permission.ACCESS_COARSE_LOCATION
        granted -= locPermission
        reachRequirements()
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { flow.events.first() }
        }
        flow.requestCurrentStatePermissions()
        assertArrayEquals(arrayOf(locPermission), (event.await() as OnboardingEvent.RequestToRequestPermissions).permissions)
    }
    @Test fun optionalDenial_doesNotBecomeCriticalRejection() {
        granted -= notification
        reachRequirements()
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(OnboardingState.NOTIFICATION, flow.currentState.value)
            flow.onHandlePermissionsResult(mapOf(notification to false))
            assertEquals(OnboardingState.ALL_SET, flow.currentState.value)
        } else {
            assertEquals(OnboardingState.ALL_SET, flow.currentState.value)
        }
    }
    @Test fun criticalDenial_canRecoverAfterPermissionsAreGranted() {
        granted -= btPermission
        reachRequirements()
        flow.onHandlePermissionsResult(mapOf(btPermission to false))
        assertEquals(OnboardingState.RUQUIRED_PERMISSION_REJECTED, flow.currentState.value)
        granted += btPermission
        flow.navigateToNextStep()
        assertEquals(OnboardingState.ALL_SET, flow.currentState.value)
    }
}
