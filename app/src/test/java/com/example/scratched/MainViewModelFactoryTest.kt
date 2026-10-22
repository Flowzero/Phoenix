package com.example.scratched

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.scratched.utilities.stateManagers.BluetoothStateManager
import com.example.scratched.utilities.stateManagers.LocationStateManager
import com.example.scratched.utilities.bluetooth.BluetoothState
import com.example.scratched.meshMVP.transport.BluetoothGattClientManager
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.example.scratched.onboarding.OnboardingState
import com.example.scratched.onboarding.OnboardingStatusRepository
import com.example.scratched.utilities.PermissionsManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelFactoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var bluetoothStateManager: BluetoothStateManager
    private lateinit var repository: OnboardingStatusRepository
    private lateinit var factory: MainViewModelFactory
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        Dispatchers.setMain(testDispatcher)

        permissionsManager = mockk(relaxed = true)
        bluetoothStateManager = mockk(relaxed = true)
        repository = mockk()
        every { repository.isFirstTimeLaunch() } returns true
        every { repository.markComplete() } just Runs
        every { bluetoothStateManager.bluetoothStateFlow } returns MutableStateFlow(BluetoothState.ENABLED)
        val scanner = mockk<BluetoothGattClientManager>(relaxed = true)
        every { scanner.discoveredDevices } returns MutableSharedFlow()
        factory = MainViewModelFactory(
            permissionsManager, bluetoothStateManager,
            mockk<LocationStateManager>(relaxed = true), scanner, repository
        )
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun create_returnsMainViewModel_forSupportedClass() {
        val viewModel: ViewModel = factory.create(MainViewModel::class.java)
        store.put("test", viewModel)

        assertEquals(MainViewModel::class.java, viewModel.javaClass)
    }

    @Test
    fun create_returnsViewModelWithWelcomeState_onFirstLaunch() {
        val viewModel = factory.create(MainViewModel::class.java)
        store.put("test", viewModel)

        assertEquals(OnboardingState.WELCOME, viewModel.onboardingState.value)
    }

    @Test
    fun create_throwsException_forUnsupportedClass() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnsupportedViewModel::class.java)
        }
    }

    private class UnsupportedViewModel : ViewModel()
}
