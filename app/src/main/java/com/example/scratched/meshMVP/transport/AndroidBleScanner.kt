package com.example.scratched.meshMVP.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.scratched.constants.AppConstants
import java.util.concurrent.atomic.AtomicBoolean

/** Thin Android adapter. Converts advertisements to transport observations, never to UI models. */
class AndroidBleScanner(context: Context) : BleScanner {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission") // Readiness is checked before start; races are handled by the controller.
    override fun start(filter: DiscoveryFilter, listener: BleScanner.Listener): ScanHandle {
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val scanner = adapter?.bluetoothLeScanner
            ?: throw ScanUnavailable(ScanPhase.ERROR, "BLE scanner is unavailable")
        val callback = object : ScanCallback() {
            private fun deliver(result: ScanResult) {
                val record = result.scanRecord
                val uuids = record?.serviceUuids?.map { it.uuid }.orEmpty()
                if (filter == DiscoveryFilter.PHOENIX_ONLY && AppConstants.GATT.SERVICE_UUID !in uuids) return
                val manufacturerData = mutableMapOf<Int, List<Byte>>()
                record?.manufacturerSpecificData?.let { data ->
                    for (i in 0 until data.size()) manufacturerData[data.keyAt(i)] = data.valueAt(i).toList()
                }
                listener.onObservation(ScanObservation(
                    address = result.device.address,
                    name = record?.deviceName,
                    rssi = result.rssi,
                    txPower = record?.txPowerLevel?.takeUnless { it == Int.MIN_VALUE },
                    serviceUuids = uuids,
                    manufacturerData = manufacturerData,
                    isConnectable = result.isConnectable
                ))
            }
            override fun onScanResult(callbackType: Int, result: ScanResult) = deliver(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::deliver)
            override fun onScanFailed(errorCode: Int) {
                listener.onFailure(AndroidScanFailures.fromCode(errorCode))
            }
        }
        val stopped = AtomicBoolean(false)
        val handle = ScanHandle {
            if (stopped.compareAndSet(false, true)) {
                try { scanner.stopScan(callback) }
                catch (e: SecurityException) { Log.w(TAG, "Permission revoked during scan cleanup", e) }
                catch (e: IllegalStateException) { Log.w(TAG, "Bluetooth unavailable during scan cleanup", e) }
            }
        }
        val filters = if (filter == DiscoveryFilter.PHOENIX_ONLY) listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(AppConstants.GATT.SERVICE_UUID)).build()
        ) else emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try { scanner.startScan(filters, settings, callback) }
        catch (e: Exception) {
            try { handle.stop() } catch (cleanup: Exception) { Log.w(TAG, "Scan cleanup failed", cleanup) }
            throw e
        }
        return handle
    }

    companion object { private const val TAG = "AndroidBleScanner" }
}
