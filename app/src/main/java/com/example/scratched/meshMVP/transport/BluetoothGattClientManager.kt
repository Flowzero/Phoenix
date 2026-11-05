package com.example.scratched.meshMVP.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.example.scratched.BleDeviceUiModel
import com.example.scratched.constants.AppConstants
import com.example.scratched.utilities.PermissionsManager
import com.example.scratched.utilities.stateManagers.UsableLocationAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Discovery only; GATT connections and the messaging protocol are not implemented yet. */
@SuppressLint("MissingPermission")
class BluetoothGattClientManager(context: Context, private val permissionsManager: PermissionsManager) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val devices = MutableSharedFlow<BleDeviceUiModel>(extraBufferCapacity = 64)
    val discoveredDevices = devices.asSharedFlow()
    private var activeScanner: BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null
    private val controller = ScanSessionController(
        now = SystemClock::elapsedRealtime,
        schedule = { wait, action ->
            val job = scope.launch { delay(wait); action() }
            val cancel: () -> Unit = { job.cancel() }
            cancel
        },
        startRadio = ::startRadio,
        stopRadio = ::stopRadio
    )
    internal val scanState = controller.state

    private fun startRadio(session: Long) {
        if (!permissionsManager.areAllRequiredPermissionsGranted()) {
            throw ScanUnavailable(ScanPhase.PERMISSION_REQUIRED, "Bluetooth scan permissions are required")
        }
        if (permissionsManager.requiresLocationServices() && !UsableLocationAdapter(appContext).isEnabled()) {
            throw ScanUnavailable(ScanPhase.ERROR, "Enable location services to scan on Android 10–11")
        }
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: throw ScanUnavailable(ScanPhase.ERROR, "Bluetooth LE is not supported")
        if (!adapter.isEnabled) throw ScanUnavailable(ScanPhase.BLUETOOTH_DISABLED, "Bluetooth is disabled")
        // Resolve afresh: Android can replace the scanner when Bluetooth is toggled.
        val scanner = adapter.bluetoothLeScanner
            ?: throw ScanUnavailable(ScanPhase.ERROR, "BLE scanner is unavailable")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch { publish(session, result) }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                scope.launch { results.forEach { publish(session, it) } }
            }
            override fun onScanFailed(errorCode: Int) {
                scope.launch { controller.fail(session, "BLE scan failed (Android code $errorCode)") }
            }
        }
        activeScanner = scanner
        activeCallback = callback
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(AppConstants.GATT.SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
    }

    private fun publish(session: Long, result: ScanResult) {
        if (!controller.accepts(session)) return
        val record = result.scanRecord ?: return
        if (record.serviceUuids?.any { it.uuid == AppConstants.GATT.SERVICE_UUID } != true) return
        val manufacturerData = mutableMapOf<Int, ByteArray>()
        record.manufacturerSpecificData?.let { data ->
            for (i in 0 until data.size()) manufacturerData[data.keyAt(i)] = data.valueAt(i).copyOf()
        }
        val time = System.currentTimeMillis()
        // Use advertised name: reading BluetoothDevice.name requires CONNECT and may be stale.
        devices.tryEmit(BleDeviceUiModel(
            address = result.device.address,
            name = record.deviceName,
            rssi = result.rssi,
            txPower = record.txPowerLevel.takeUnless { it == Int.MIN_VALUE },
            serviceUuids = record.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
            manufacturerData = manufacturerData,
            isConnectable = result.isConnectable,
            firstSeenAtMillis = time,
            lastSeenAtMillis = time,
            scanCount = 1
        ))
    }

    private fun stopRadio() {
        val scanner = activeScanner
        val callback = activeCallback
        activeScanner = null
        activeCallback = null
        if (scanner != null && callback != null) {
            try { scanner.stopScan(callback) }
            catch (e: SecurityException) { Log.w(TAG, "Scan permission revoked during cleanup", e) }
            catch (e: IllegalStateException) { Log.w(TAG, "Bluetooth disabled during cleanup", e) }
        }
    }

    fun startScanning() = controller.start()
    fun stopScanning() = controller.stop()
    fun close() { controller.close(); scope.cancel() }

    companion object { private const val TAG = "BluetoothGattClient" }
}
