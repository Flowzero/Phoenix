package com.example.scratched.meshMVP.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.scratched.BleDeviceUiModel

import com.example.scratched.constants.AppConstants
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Manages GATT client side operations: scanning and client-side connections
 */

@SuppressLint("MissingPermission")
class BluetoothGattClientManager(
    private val context: Context,
    private val connectionScope: CoroutineScope,
    private val permissionsManager: PermissionsManager,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis

) {
    companion object {
        const val TAG = "BluetoothGattClientManager"
    }

    // <TEST IMPLEMENTATION> -----------------------------------------------------------------------
    private val _discoveredDevices = MutableSharedFlow<BleDeviceUiModel>(extraBufferCapacity = 64)
    val discoveredDevices: SharedFlow<BleDeviceUiModel> = _discoveredDevices.asSharedFlow()
    // </TEST IMPLEMENTATION> ----------------------------------------------------------------------

    private var isScanning = false
    private var lastScanTime = 0L

    // Limit scan rate to 5s to prevent "scanning too frequently error"
    private val scanRateLimit = 5000L

    // Unable to test without Android-specific components, no need to use wrapper
    // Using raw Android API (BluetoothAdapter)

    // https://punchthrough.com/android-ble-guide/
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val scanFilters = listOf(
        ScanFilter
            .Builder()
            .setServiceUuid(
                ParcelUuid.fromString(AppConstants.GATT.SERVICE_UUID.toString())
        ).build()
    )
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result.device) {
                Log.i("ScanCallback", "Found BLE device.\nName: ${this?.name ?: "Unnamed"}\nAddress: ${this?.address}")
            }
            handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)

            when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> {
                    Log.e(TAG, "SCAN_FAILED_ALREADY_STARTED")
                    stopScanning()
                    // do something else if required
                }
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> {
                    Log.e(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED")
                }
                SCAN_FAILED_INTERNAL_ERROR -> {
                    Log.e(TAG, "SCAN_FAILED_INTERNAL_ERROR")
                }
                SCAN_FAILED_FEATURE_UNSUPPORTED -> {
                    Log.e(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED")
                    // cannot do anything
                }
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> {
                    Log.e(TAG, "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES")
                    // should wait for other apps to free up resources
                }
                else -> {
                    Log.e(TAG, "Unknown scan failure code: $errorCode")
                }
            }
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.device
        val deviceAddress = device.address
        val scanRecord = result.scanRecord

        // process only devices that have our service UUID
        val hasOurService = scanRecord?.serviceUuids?.any {
            it.uuid == AppConstants.GATT.SERVICE_UUID
        } == true
        //if (!hasOurService) return

    // <TEST IMPLEMENTATION> -----------------------------------------------------------------------
        val uiModel = parseScanResultToUiModel(device.address, device.name, result.rssi, scanRecord)
        connectionScope.launch {
            _discoveredDevices.emit(uiModel)
        }
    }

    private fun parseScanResultToUiModel(
        address: String,
        name: String?,
        rssi: Int,
        scanRecord: android.bluetooth.le.ScanRecord?
    ): BleDeviceUiModel {
        val currentTime = currentTimeMillis()

        val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()

        val manufacturerData = mutableMapOf<Int, ByteArray>()
        scanRecord?.manufacturerSpecificData?.let { msd ->
            for (i in 0 until msd.size()) {
                manufacturerData[msd.keyAt(i)] = msd.valueAt(i)
            }
        }

        return BleDeviceUiModel(
            address = address,
            name = name,
            rssi = rssi,
            txPower = scanRecord?.txPowerLevel,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            isConnectable = true, // temp const val
            firstSeenAtMillis = currentTime,
            lastSeenAtMillis = currentTime,
            scanCount = 1
        )
    }
    // </TEST IMPLEMENTATION> ----------------------------------------------------------------------

    fun startScanning() {
        if (!permissionsManager.areAllRequiredPermissionsGranted()) {
            Log.e(TAG, "Required permissions are not granted!")
            return
        }
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is NULL! Cannot perform a scan")
            return
        }
        if (isScanning) {
            Log.d(TAG, "Skipping start scanning; Scan is already in progress")
            return
        }

        // check if need to wait before performing a scan
        // to prevent "scanning too frequently error
        val currentTime = currentTimeMillis()
        val timeSinceLastStart = currentTime - lastScanTime
        if (timeSinceLastStart < scanRateLimit) {
            val remainingWait = scanRateLimit - timeSinceLastStart
            Log.w(TAG, "Need to wait ${remainingWait}ms before starting new scan")

            connectionScope.launch {
                delay(remainingWait)
                if (!isScanning) {
                    startScanning()
                }
            }
            return
        }

        Log.d(TAG, "Starting BLE scan with target service UUID: ${AppConstants.GATT.SERVICE_UUID}")

        try {
            lastScanTime = currentTime
            isScanning = true

            // null scanFilters for all devices
            bleScanner?.startScan(null, scanSettings, scanCallback)
            Log.d(TAG, "BLE scan started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan: ${e.message}")
            isScanning = false
        }

    }

    fun stopScanning() {
        if (bluetoothAdapter == null){
            return
        }
        if (!permissionsManager.hasBluetoothPermissions()) {
            return
        }

        if (isScanning) {
            try {
                scanCallback.let {
                    bleScanner?.stopScan(it)
                    Log.d(TAG, "BLE scan stopped successfully")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }

            isScanning = false
        }
    }
}
