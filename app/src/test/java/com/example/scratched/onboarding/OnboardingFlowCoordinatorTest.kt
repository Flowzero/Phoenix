package com.example.scratched.onboarding

import android.Manifest
import android.util.Log
import com.example.scratched.mesh.BluetoothStateManager
import com.example.scratched.utilities.PermissionsManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OnboardingFlowCoordinatorTest {

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
        every { repository.markComplete() } just Runs
        every { bluetoothStateManager.isEnabled() } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createCoordinator(isFirstTimeLaunch: Boolean): OnboardingFlowCoordinator {
        every { repository.isFirstTimeLaunch() } returns isFirstTimeLaunch

        return OnboardingFlowCoordinator(
            permissionsManager = permissionsManager,
            bluetoothStateManager = bluetoothStateManager,
            onboardingStatusRepository = repository
        )
    }

    private fun allowAllCheckedPermissions() {
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(any()) } returns true
        every { bluetoothStateManager.isEnabled() } returns true
    }

    @Test
    fun startAppFlow_startsAtWelcome_onFirstLaunch() {
        val coordinator = createCoordinator(isFirstTimeLaunch = true)

        coordinator.startAppFlow()

        assertEquals(OnboardingState.WELCOME, coordinator.currentState.value)
    }

    @Test
    fun startAppFlow_completesOnboarding_forReturningUserWithAllPermissions() {
        every { permissionsManager.getMissingRequiredPermissions() } returns emptyList()
        val coordinator = createCoordinator(isFirstTimeLaunch = false)

        coordinator.startAppFlow()

        assertEquals(OnboardingState.COMPLETED, coordinator.currentState.value)
        verify(exactly = 1) { repository.markComplete() }
    }

    @Test
    fun startAppFlow_startsAtBluetooth_whenReturningUserMissesBluetoothPermission() {
        val bluetoothPermission = Manifest.permission.BLUETOOTH_SCAN
        every { permissionsManager.getMissingRequiredPermissions() } returns
            listOf(bluetoothPermission)
        every { permissionsManager.getBluetoothPermissions() } returns
            listOf(bluetoothPermission)
        every { permissionsManager.getLocationPermissions() } returns
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val coordinator = createCoordinator(isFirstTimeLaunch = false)

        coordinator.startAppFlow()

        assertEquals(OnboardingState.BLUETOOTH, coordinator.currentState.value)
    }

    @Test
    fun startAppFlow_startsAtLocation_whenReturningUserMissesLocationPermission() {
        val bluetoothPermission = Manifest.permission.BLUETOOTH_SCAN
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        every { permissionsManager.getMissingRequiredPermissions() } returns
            listOf(locationPermission)
        every { permissionsManager.getBluetoothPermissions() } returns
            listOf(bluetoothPermission)
        every { permissionsManager.getLocationPermissions() } returns
            listOf(locationPermission)
        val coordinator = createCoordinator(isFirstTimeLaunch = false)

        coordinator.startAppFlow()

        assertEquals(OnboardingState.LOCATION, coordinator.currentState.value)
    }

    @Test
    fun startAppFlow_startsAtEnableBluetooth_whenReturningUserHasPermissionsButBluetoothIsDisabled() {
        every { permissionsManager.getMissingRequiredPermissions() } returns emptyList()
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { bluetoothStateManager.isEnabled() } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = false)

        coordinator.startAppFlow()

        assertEquals(OnboardingState.ENABLE_BLUETOOTH, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_movesFromWelcomeToAbout() {
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.ABOUT, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_movesFromAboutToBluetooth_whenBluetoothPermissionIsMissing() {
        val bluetoothPermission = Manifest.permission.BLUETOOTH_SCAN
        every { permissionsManager.getBluetoothPermissions() } returns
            listOf(bluetoothPermission)
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(bluetoothPermission) } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.BLUETOOTH, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_movesFromAboutToLocation_whenLocationPermissionIsMissing() {
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.getLocationPermissions() } returns
            listOf(locationPermission)
        every { permissionsManager.isPermissionGranted(locationPermission) } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.LOCATION, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_movesFromAboutToEnableBluetooth_whenBluetoothIsDisabled() {
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(any()) } returns true
        every { bluetoothStateManager.isEnabled() } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.ENABLE_BLUETOOTH, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_movesFromEnableBluetoothToAllSet_whenBluetoothBecomesEnabled() {
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(any()) } returns true
        every { bluetoothStateManager.isEnabled() } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()
        coordinator.navigateToNextStep()
        every { bluetoothStateManager.isEnabled() } returns true

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.ALL_SET, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_completesReturningUserFlow_whenBluetoothBecomesEnabled() {
        every { permissionsManager.getMissingRequiredPermissions() } returns emptyList()
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(any()) } returns true
        every { bluetoothStateManager.isEnabled() } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = false)
        coordinator.startAppFlow()
        every { bluetoothStateManager.isEnabled() } returns true

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.COMPLETED, coordinator.currentState.value)
        verify(exactly = 1) { repository.markComplete() }
    }

    @Test
    fun navigateToNextStep_movesFromAboutToAllSet_whenRequiredPermissionsAreGranted() {
        allowAllCheckedPermissions()
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.ALL_SET, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_completesOnboardingAndSavesResult_afterAllSet() {
        allowAllCheckedPermissions()
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.COMPLETED, coordinator.currentState.value)
        verify(exactly = 1) { repository.markComplete() }
    }

    @Test
    fun requestCurrentStatePermissions_emitsBluetoothPermissions() = runBlocking {
        val bluetoothPermissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        every { permissionsManager.getBluetoothPermissions() } returns bluetoothPermissions
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(any()) } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()
        coordinator.navigateToNextStep()
        val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.events.first()
        }

        coordinator.requestCurrentStatePermissions()

        val event = receivedEvent.await() as OnboardingEvent.RequestToRequestPermissions
        assertArrayEquals(bluetoothPermissions.toTypedArray(), event.permissions)
    }

    @Test
    fun onHandlePermissionsResult_movesToRejectedState_whenRequiredPermissionIsDenied() {
        val deniedPermission = Manifest.permission.BLUETOOTH_SCAN
        every { permissionsManager.getRequiredPermissions() } returns
            listOf(deniedPermission)
        val coordinator = createCoordinator(isFirstTimeLaunch = true)

        coordinator.onHandlePermissionsResult(mapOf(deniedPermission to false))

        assertEquals(
            OnboardingState.RUQUIRED_PERMISSION_REJECTED,
            coordinator.currentState.value
        )
    }

    @Test
    fun onHandlePermissionsResult_movesForward_whenOnlyOptionalPermissionIsDenied() {
        every { permissionsManager.getRequiredPermissions() } returns emptyList()
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()

        coordinator.onHandlePermissionsResult(
            mapOf(Manifest.permission.POST_NOTIFICATIONS to false)
        )

        assertEquals(OnboardingState.ABOUT, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_keepsCompletedStateAndDoesNotSaveAgain() {
        allowAllCheckedPermissions()
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()
        coordinator.navigateToNextStep()
        coordinator.navigateToNextStep()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.COMPLETED, coordinator.currentState.value)
        verify(exactly = 1) { repository.markComplete() }
    }

    @Test
    fun requestCurrentStatePermissions_emitsMissingLocationPermission() = runBlocking {
        val bluetoothPermission = Manifest.permission.BLUETOOTH_SCAN
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        every { permissionsManager.getBluetoothPermissions() } returns
            listOf(bluetoothPermission)
        every { permissionsManager.getLocationPermissions() } returns
            listOf(locationPermission)
        every { permissionsManager.isPermissionGranted(bluetoothPermission) } returns true
        every { permissionsManager.isPermissionGranted(locationPermission) } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()
        coordinator.navigateToNextStep()
        val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.events.first()
        }

        coordinator.requestCurrentStatePermissions()

        val event = receivedEvent.await() as OnboardingEvent.RequestToRequestPermissions
        assertArrayEquals(arrayOf(locationPermission), event.permissions)
    }

    @Test
    fun requestCurrentStatePermissions_movesForward_whenCurrentPermissionIsAlreadyGranted() {
        val bluetoothPermission = Manifest.permission.BLUETOOTH_SCAN
        every { permissionsManager.getBluetoothPermissions() } returns
            listOf(bluetoothPermission)
        every { permissionsManager.getLocationPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(bluetoothPermission) } returns false
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.startAppFlow()
        coordinator.navigateToNextStep()
        coordinator.navigateToNextStep()
        every { permissionsManager.getBluetoothPermissions() } returns emptyList()
        every { permissionsManager.isPermissionGranted(any()) } returns true

        coordinator.requestCurrentStatePermissions()

        assertEquals(OnboardingState.ALL_SET, coordinator.currentState.value)
    }

    @Test
    fun navigateToNextStep_recoversFromRejectedState_whenPermissionsAreGranted() {
        val deniedPermission = Manifest.permission.BLUETOOTH_SCAN
        every { permissionsManager.getRequiredPermissions() } returns
            listOf(deniedPermission)
        val coordinator = createCoordinator(isFirstTimeLaunch = true)
        coordinator.onHandlePermissionsResult(mapOf(deniedPermission to false))
        allowAllCheckedPermissions()

        coordinator.navigateToNextStep()

        assertEquals(OnboardingState.ALL_SET, coordinator.currentState.value)
    }

}
