package com.example.scratched.mesh

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.SparseArray
import com.example.scratched.BleDeviceUiModel
import com.example.scratched.constants.AppConstants
import com.example.scratched.support.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothGattClientManagerTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun start_usesLowLatencyAndCallback() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning()
        verify(exactly = 1) { f.scanner.startScan(isNull<List<ScanFilter>>(), match<ScanSettings> { it.scanMode == ScanSettings.SCAN_MODE_LOW_LATENCY }, any<ScanCallback>()) }
        assertTrue(f.callback.isCaptured)
    }
    @Test fun missingPermissions_preventsRadioStart() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        every { f.permissions.areAllRequiredPermissionsGranted() } returns false
        f.client.startScanning()
        verify(exactly = 0) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
    }
    @Test fun missingAdapter_preventsRadioStart() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope, hasAdapter = false)
        f.client.startScanning(); f.client.stopScanning()
        verify(exactly = 0) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
        verify(exactly = 0) { f.scanner.stopScan(any<ScanCallback>()) }
    }
    @Test fun repeatedStart_doesNotCreateSecondScan() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning(); f.client.startScanning()
        verify(exactly = 1) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
    }
    @Test fun stop_usesOriginalCallbackAndIsIdempotent() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning()
        f.client.stopScanning(); f.client.stopScanning()
        verify(exactly = 1) { f.scanner.stopScan(f.callback.captured) }
    }
    @Test fun stopBeforeStart_doesNotCallRadio() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.stopScanning()
        verify(exactly = 0) { f.scanner.stopScan(any<ScanCallback>()) }
    }
    @Test fun stopWithoutPermission_doesNotCallRestrictedApi() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning()
        every { f.permissions.hasBluetoothPermissions() } returns false
        f.client.stopScanning()
        verify(exactly = 0) { f.scanner.stopScan(any<ScanCallback>()) }
    }
    @Test fun startException_isContainedAndRetryIsPossible() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        every { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) } throws SecurityException("revoked")
        f.client.startScanning()
        advanceTimeBy(5000); runCurrent()
        every { f.scanner.startScan(any(), any(), capture(f.callback)) } just Runs
        f.client.startScanning()
        assertTrue(f.callback.isCaptured)
        verify(exactly = 2) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
    }
    @Test fun stopException_isContainedAndLocalScanCanRestart() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning()
        every { f.scanner.stopScan(any<ScanCallback>()) } throws IllegalStateException("radio stopped")
        f.client.stopScanning()
        advanceTimeBy(5000); runCurrent()
        f.client.startScanning()
        verify(exactly = 2) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
    }
    @Test fun cooldown_defersNextScanUntilFiveSeconds() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning(); f.client.stopScanning(); f.client.startScanning()
        verify(exactly = 1) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
        advanceTimeBy(4999); runCurrent()
        verify(exactly = 1) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
        advanceTimeBy(1); runCurrent()
        verify(exactly = 2) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
    }
    @Test fun cooldown_rechecksPermissionsBeforeDeferredRadioStart() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning(); f.client.stopScanning(); f.client.startScanning()
        every { f.permissions.areAllRequiredPermissionsGranted() } returns false
        advanceTimeBy(5000); runCurrent()
        verify(exactly = 1) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
        every { f.permissions.areAllRequiredPermissionsGranted() } returns true
        f.client.startScanning()
        verify(exactly = 2) { f.scanner.startScan(isNull<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
    }
    @Test fun alreadyStartedFailure_stopsExistingScan() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        f.client.startScanning()
        f.callback.captured.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED)
        verify(exactly = 1) { f.scanner.stopScan(any<ScanCallback>()) }
    }
    @Test fun resultWithoutScanRecord_usesEmptyOptionalMetadata() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        val received = mutableListOf<BleDeviceUiModel>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.client.discoveredDevices.toList(received) }
        f.client.startScanning()
        f.callback.captured.onScanResult(0, f.result(name = null, rssi = -75))
        val value = received.single()
        assertEquals("AA:BB:CC:DD:EE:01", value.address)
        assertEquals("Unknown device", value.displayName)
        assertEquals(-75, value.rssi)
        assertNull(value.txPower)
        assertTrue(value.serviceUuids.isEmpty())
        assertTrue(value.manufacturerData.isEmpty())
        assertEquals(value.firstSeenAtMillis, value.lastSeenAtMillis)
        assertEquals(1, value.scanCount)
    }
    @Test fun advertisementMetadata_reachesDiscoveryFlow() = runTest(main.dispatcher) {
        val f = ScannerFixture(backgroundScope)
        val received = mutableListOf<BleDeviceUiModel>()
        val manufacturer = SparseArray<ByteArray>().apply { put(0x1234, byteArrayOf(1, 2, 3)) }
        val record = mockk<ScanRecord> {
            every { serviceUuids } returns listOf(ParcelUuid(AppConstants.GATT.SERVICE_UUID))
            every { manufacturerSpecificData } returns manufacturer
            every { txPowerLevel } returns -12
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.client.discoveredDevices.toList(received) }
        f.client.startScanning()
        f.callback.captured.onScanResult(0, f.result(record = record))
        val value = received.single()
        assertEquals(listOf(AppConstants.GATT.SERVICE_UUID.toString()), value.serviceUuids)
        assertArrayEquals(byteArrayOf(1, 2, 3), value.manufacturerData[0x1234])
        assertEquals(-12, value.txPower)
    }
}
