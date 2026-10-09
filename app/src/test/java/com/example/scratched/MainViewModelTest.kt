package com.example.scratched

import android.content.Intent
import android.util.Log
import com.example.scratched.mesh.BluetoothStateManager
import com.example.scratched.onboarding.OnboardingEvent
import com.example.scratched.onboarding.OnboardingFlowCoordinator
import com.example.scratched.onboarding.OnboardingState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var coordinator: OnboardingFlowCoordinator
    private lateinit var bluetoothStateManager: BluetoothStateManager
    private lateinit var coordinatorState: MutableStateFlow<OnboardingState>
    private lateinit var coordinatorEvents: MutableSharedFlow<OnboardingEvent>
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        coordinator = mockk(relaxed = true)
        bluetoothStateManager = mockk()
        coordinatorState = MutableStateFlow(OnboardingState.WELCOME)
        coordinatorEvents = MutableSharedFlow()

        every { coordinator.currentState } returns coordinatorState
        every { coordinator.events } returns coordinatorEvents
        every { coordinator.startAppFlow() } just Runs

        viewModel = MainViewModel(coordinator, bluetoothStateManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun initialization_startsOnboardingFlow() {
        verify(exactly = 1) { coordinator.startAppFlow() }
    }

    @Test
    fun onboardingState_exposesCoordinatorState() {
        coordinatorState.value = OnboardingState.ABOUT

        assertEquals(OnboardingState.ABOUT, viewModel.onboardingState.value)
    }

    @Test
    fun onNextClicked_delegatesNavigationToCoordinator() {
        viewModel.onNextClicked()

        verify(exactly = 1) { coordinator.navigateToNextStep() }
    }

    @Test
    fun onRequestPermissionsClicked_delegatesRequestToCoordinator() {
        viewModel.onRequestPermissionsClicked()

        verify(exactly = 1) { coordinator.requestCurrentStatePermissions() }
    }

    @Test
    fun onPermissionsResult_passesResultToCoordinator() {
        val result = mapOf(
            "permission.one" to true,
            "permission.two" to false
        )

        viewModel.onPermissionsResult(result)

        verify(exactly = 1) { coordinator.onHandlePermissionsResult(result) }
    }

    @Test
    fun requestPermissionsEvent_isConvertedToActivityEvent() = runTest(testDispatcher) {
        val permissions = arrayOf("permission.one", "permission.two")
        val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.activityEvents.first()
        }

        coordinatorEvents.emit(OnboardingEvent.RequestToRequestPermissions(permissions))

        assertEquals(
            ActivityEvent.RequestPermissions(permissions),
            receivedEvent.await()
        )
    }

    @Test
    fun showErrorEvent_isConvertedToActivityEvent() = runTest(testDispatcher) {
        val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.activityEvents.first()
        }

        coordinatorEvents.emit(OnboardingEvent.RequestToShowError("Test error"))

        assertEquals(ActivityEvent.ShowError("Test error"), receivedEvent.await())
    }

    @Test
    fun onBluetoothEnableResult_navigatesForward_whenBluetoothWasEnabled() {
        viewModel.onBluetoothEnableResult(isSuccess = true)

        verify(exactly = 1) { coordinator.navigateToNextStep() }
    }

    @Test
    fun onBluetoothEnableResult_emitsError_whenBluetoothWasNotEnabled() =
        runTest(testDispatcher) {
            val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.activityEvents.first()
            }

            viewModel.onBluetoothEnableResult(isSuccess = false)

            assertEquals(
                ActivityEvent.ShowError(
                    "User declined/Error happened: Bluetooth is required"
                ),
                receivedEvent.await()
            )
        }

    @Test
    fun onEnableBluetoothClicked_emitsEnableBluetooth_whenIntentIsAvailable() =
        runTest(testDispatcher) {
            val intent = mockk<Intent>()
            every { bluetoothStateManager.getEnableBluetoothIntent() } returns intent
            val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.activityEvents.first()
            }

            viewModel.onEnableBluetoothClicked()

            assertEquals(ActivityEvent.EnableBluetooth(intent), receivedEvent.await())
        }

    @Test
    fun onEnableBluetoothClicked_emitsError_whenIntentIsUnavailable() =
        runTest(testDispatcher) {
            every { bluetoothStateManager.getEnableBluetoothIntent() } returns null
            val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.activityEvents.first()
            }

            viewModel.onEnableBluetoothClicked()

            assertEquals(
                ActivityEvent.ShowError("Unable to request EnableBluetooth"),
                receivedEvent.await()
            )
        }

    @Test
    fun onEnableBluetoothClicked_requestsIntentOnce() = runTest(testDispatcher) {
        every { bluetoothStateManager.getEnableBluetoothIntent() } returns null
        val receivedEvent = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.activityEvents.first()
        }

        viewModel.onEnableBluetoothClicked()
        receivedEvent.await()

        verify(exactly = 1) { bluetoothStateManager.getEnableBluetoothIntent() }
    }
}
