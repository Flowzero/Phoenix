package com.example.scratched.onboarding

import android.Manifest
import android.os.Build
import android.util.Log
import com.example.scratched.mesh.BluetoothStateManager
import com.example.scratched.utilities.PermissionsManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class OnboardingFlowCoordinatorApiTest {

    private lateinit var permissionsManager: PermissionsManager
    private lateinit var bluetoothStateManager: BluetoothStateManager
    private lateinit var repository: OnboardingStatusRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        permissionsManager = mockk()
        bluetoothStateManager = mockk()
        repository = mockk()
        every { repository.isFirstTimeLaunch() } returns true
        every { repository.markComplete() } just Runs
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { bluetoothStateManager.isEnabled() } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createCoordinator(): OnboardingFlowCoordinator {
        return OnboardingFlowCoordinator(
            permissionsManager,
            bluetoothStateManager,
            repository
        )
    }

    @Test
    fun navigateToNextStep_movesToNotification_onAndroid13WhenPermissionIsMissing() {
        every {
            permissionsManager.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } returns false
        val coordinator = createCoordinator()
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.NOTIFICATION, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_skipsNotification_whenPermissionIsAlreadyGranted() {
        every {
            permissionsManager.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } returns true
        val coordinator = createCoordinator()
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.ALL_SET, coordinator.currentState.value)
    }

    @Test
    fun requestCurrentStatePermissions_emitsNotificationPermission_onAndroid13() =
        runBlocking {
            every {
                permissionsManager.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
            } returns false
            every { permissionsManager.getNotificationPermissions() } returns
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            val coordinator = createCoordinator()
            coordinator.startAppFlow()
            coordinator.navigateToNextStep()
            coordinator.navigateToNextStep()
            val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.events.first()
            }

            coordinator.requestCurrentStatePermissions()

            val event = receivedEvent.await() as OnboardingEvent.RequestToRequestPermissions
            assertArrayEquals(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                event.permissions
            )
        }
}
