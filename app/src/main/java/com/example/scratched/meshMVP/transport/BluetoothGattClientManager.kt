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

import com.example.scratched.constants.AppConstants
import com.example.scratched.utilities.PermissionsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val permissionsManager: PermissionsManager

) {
    companion object {
        const val TAG = "BluetoothGattClientManager"
    }

    // <TEST IMPLEMENTATION> -----------------------------------------------------------------------
    private val _discoveredDevices = MutableSharedFlow<BleScanResult>(extraBufferCapacity = 64)
    val discoveredDevices: SharedFlow<BleScanResult> = _discoveredDevices.asSharedFlow()
    // </TEST IMPLEMENTATION> ----------------------------------------------------------------------

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError = _scanError.asStateFlow()
    private var pendingScanJob: Job? = null
    private var scanGeneration = 0L
    private var activeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var lastScanTime = 0L

    // Limit scan rate to 5s to prevent "scanning too frequently error"
    private val scanRateLimit = 5000L

    // Unable to test without Android-specific components, no need to use wrapper
    // Using raw Android API (BluetoothAdapter)

    // https://punchthrough.com/android-ble-guide/
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

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
            handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            stopScanning()
            _scanError.value = "BLE scan failed (Android code $errorCode)"

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
        val scanRecord = result.scanRecord
        val serviceUuid = ParcelUuid(AppConstants.GATT.SERVICE_UUID)
        val hasOurService = scanRecord?.serviceUuids?.contains(serviceUuid) == true ||
            scanRecord?.getServiceData(serviceUuid) != null
        if (!hasOurService) return

        val name = PhoenixAdvertisement.deviceName(scanRecord?.getServiceData(serviceUuid))
            ?: scanRecord?.deviceName
        val scanResult = parseScanResult(result.device.address, name, result.rssi, scanRecord, result.isConnectable)
        connectionScope.launch { _discoveredDevices.emit(scanResult) }
    }

    private fun parseScanResult(
        address: String,
        name: String?,
        rssi: Int,
        scanRecord: android.bluetooth.le.ScanRecord?,
        isConnectable: Boolean
    ): BleScanResult {
        val currentTime = System.currentTimeMillis()

        val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()

        val manufacturerData = mutableMapOf<Int, ByteArray>()
        scanRecord?.manufacturerSpecificData?.let { msd ->
            for (i in 0 until msd.size()) {
                manufacturerData[msd.keyAt(i)] = msd.valueAt(i)
            }
        }

        return BleScanResult(
            address = address,
            name = name,
            rssi = rssi,
            txPower = scanRecord?.txPowerLevel?.takeUnless { it == Int.MIN_VALUE },
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            isConnectable = isConnectable,
            observedAtMillis = currentTime,
            serviceData = scanRecord?.serviceData?.mapKeys { it.key.uuid.toString() }
                ?.mapValues { it.value.copyOf() } ?: emptyMap()
        )
    }
    // </TEST IMPLEMENTATION> ----------------------------------------------------------------------

    @Synchronized fun startScanning() {
        if (isScanning || pendingScanJob?.isActive == true) return
        _scanError.value = null
        if (!permissionsManager.areAllRequiredPermissionsGranted()) {
            _scanError.value = "Required permissions are not granted"
            Log.e(TAG, "Required permissions are not granted!")
            return
        }
        if (bluetoothAdapter == null) {
            _scanError.value = "Bluetooth adapter is unavailable"
            Log.e(TAG, "Bluetooth adapter is NULL! Cannot perform a scan")
            return
        }
        if (isScanning) {
            Log.d(TAG, "Skipping start scanning; Scan is already in progress")
            return
        }

        // check if need to wait before performing a scan
        // to prevent "scanning too frequently error
        val currentTime = System.currentTimeMillis()
        val timeSinceLastStart = currentTime - lastScanTime
        if (timeSinceLastStart < scanRateLimit) {
            val remainingWait = scanRateLimit - timeSinceLastStart
            Log.w(TAG, "Need to wait ${remainingWait}ms before starting new scan")

            val generation = scanGeneration
            pendingScanJob = connectionScope.launch {
                delay(remainingWait)
                synchronized(this@BluetoothGattClientManager) {
                    if (generation == scanGeneration) {
                        pendingScanJob = null
                        startScanning()
                    }
                }
            }
            return
        }

        Log.d(TAG, "Starting BLE scan with target service UUID: ${AppConstants.GATT.SERVICE_UUID}")

        try {
            lastScanTime = currentTime
            isScanning = true

            val scanner = bleScanner ?: error("BLE scanner is unavailable. Enable Bluetooth")
            activeScanner = scanner
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            Log.d(TAG, "BLE scan started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan: ${e.message}")
            stopScanning()
            _scanError.value = e.message ?: "Unable to start BLE scan"
        }

    }

    @Synchronized fun stopScanning() {
        scanGeneration++
        pendingScanJob?.cancel()
        pendingScanJob = null
        val scanner = activeScanner
        activeScanner = null
        isScanning = false
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
        }
    }
}
