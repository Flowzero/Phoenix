package com.example.scratched.meshMVP.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import com.example.scratched.constants.AppConstants
import com.example.scratched.utilities.PermissionsManager
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdvertisingPhase { IDLE, STARTING, ACTIVE, STOPPED, ERROR }
data class AdvertisingState(
    val phase: AdvertisingPhase = AdvertisingPhase.IDLE,
    val error: String? = null,
    val androidErrorCode: Int? = null
)

/** Advertises discovery packets. Opening a GATT server and sending messages are separate steps. */
class BluetoothGattServerManager(context: Context, private val permissionsManager: PermissionsManager) {
    private val appContext = context.applicationContext
    private val payload = PhoenixAdvertisement.encode(SecureRandom().nextInt())
    val advertisedDeviceName: String = requireNotNull(PhoenixAdvertisement.deviceName(payload))
    private val mutableState = MutableStateFlow(AdvertisingState())
    val advertisingState = mutableState.asStateFlow()
    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null
    private var closed = false

    @SuppressLint("MissingPermission")
    @Synchronized fun startAdvertising() {
        if (closed || callback != null) return
        try {
            if (!permissionsManager.hasAdvertisingPermissions()) {
                fail("Разрешите приложению рекламу Bluetooth-устройства")
                return
            }
            val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null || !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                fail("Устройство не поддерживает Bluetooth LE")
                return
            }
            if (!adapter.isEnabled) {
                fail("Включите Bluetooth")
                return
            }
            if (!adapter.isMultipleAdvertisementSupported) {
                fail("Телефон не поддерживает BLE advertising")
                return
            }
            val currentAdvertiser = adapter.bluetoothLeAdvertiser
            if (currentAdvertiser == null) {
                fail("BLE advertiser недоступен")
                return
            }
            val currentCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    synchronized(this@BluetoothGattServerManager) {
                        if (callback !== this) {
                            stopSafely(currentAdvertiser, this)
                            return
                        }
                        mutableState.value = AdvertisingState(AdvertisingPhase.ACTIVE)
                    }
                }
                override fun onStartFailure(errorCode: Int) {
                    synchronized(this@BluetoothGattServerManager) {
                        if (callback !== this) return
                        fail("${failureMessage(errorCode)} (Android code $errorCode)", errorCode)
                    }
                }
            }
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true) // Legacy scannable ADV; no GATT connection is opened here.
                .setTimeout(0)
                .build()
            val serviceUuid = ParcelUuid(AppConstants.GATT.SERVICE_UUID)
            // Legacy packets are limited to 31 bytes each. Do not duplicate the 128-bit UUID
            // plus service data in one packet, or include the phone's unbounded Bluetooth name.
            val data = AdvertiseData.Builder()
                .addServiceUuid(serviceUuid)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()
            val response = AdvertiseData.Builder()
                .addServiceData(serviceUuid, payload.copyOf())
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .build()
            advertiser = currentAdvertiser
            callback = currentCallback
            mutableState.value = AdvertisingState(AdvertisingPhase.STARTING)
            // A state observer can stop advertising while STARTING is being published.
            if (callback === currentCallback && !closed) {
                currentAdvertiser.startAdvertising(settings, data, response, currentCallback)
            }
        } catch (e: SecurityException) {
            fail("Разрешение Bluetooth отозвано. Выдайте его в настройках приложения")
        } catch (e: Exception) {
            fail("Не удалось запустить рекламу: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    @Synchronized fun stopAdvertising() {
        val wasActive = callback != null
        releaseAdvertising()
        if (wasActive) mutableState.value = AdvertisingState(AdvertisingPhase.STOPPED)
    }

    @Synchronized fun close() {
        closed = true
        stopAdvertising()
    }

    private fun fail(message: String, code: Int? = null) {
        releaseAdvertising()
        mutableState.value = AdvertisingState(AdvertisingPhase.ERROR, message, code)
    }

    private fun releaseAdvertising() {
        val oldAdvertiser = advertiser
        val oldCallback = callback
        advertiser = null
        callback = null
        if (oldAdvertiser != null && oldCallback != null) stopSafely(oldAdvertiser, oldCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopSafely(advertiser: BluetoothLeAdvertiser, callback: AdvertiseCallback) {
        try { advertiser.stopAdvertising(callback) }
        catch (e: Exception) { Log.w(TAG, "Unable to stop advertising after Bluetooth/permission change", e) }
    }

    private fun failureMessage(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Слишком большой пакет рекламы"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Нет свободных ресурсов BLE advertising"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Реклама уже запущена"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Внутренняя ошибка BLE advertising"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising не поддерживается"
        else -> "Неизвестная ошибка BLE advertising"
    }

    companion object { private const val TAG = "BluetoothGattServer" }
}
