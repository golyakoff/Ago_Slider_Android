package net.agolyakov.agoslider.service.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.agolyakov.agoslider.data.model.ble.ConnectionState
import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarm
import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarmType
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.ble.AgoSliderTime
import net.agolyakov.agoslider.service.bluetooth.handlers.AgingOffsetReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.AutoBrightnessReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.ManualBrightnessReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.OnOffReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.RtcTemperatureReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.TimeReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.TurnOffAlarmReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.TurnOnAlarmReadCharacteristicHandler
import net.agolyakov.agoslider.service.bluetooth.handlers.VersionReadCharacteristicHandler
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BluetoothService @Inject constructor(
    private val bluetoothAdapterProvider: BluetoothAdapterProvider
) {
    private val _tag = "BluetoothService"

    private var isOtaUpdateMode = false

    fun enterOtaUpdateMode() {
        isOtaUpdateMode = true
        Log.d("BluetoothService", "Entered OTA update mode - connections will be preserved")
    }

    fun exitOtaUpdateMode() {
        isOtaUpdateMode = false
        Log.d("BluetoothService", "Exited OTA update mode")
    }

    fun shouldPreserveConnection(): Boolean {
        return isOtaUpdateMode
    }

    // Firmware Version
    private var _firmwareVersion: String = "Unknown"
    private val _agoSliderFirmwareVersion = MutableStateFlow(_firmwareVersion)
    val agoSliderFirmwareVersion: StateFlow<String> = _agoSliderFirmwareVersion
    private val _versionReadEvent = MutableStateFlow<Unit?>(null)
    private val firmwareVersionReadCharacteristicHandler = VersionReadCharacteristicHandler(
        version = _agoSliderFirmwareVersion,
        readEvent = _versionReadEvent
    )

    // Time
    private var _bleDeviceTime: AgoSliderTime = AgoSliderTime()
    private var _agoSliderDeviceTime = MutableStateFlow(_bleDeviceTime)
    val agoSliderDeviceTime: StateFlow<AgoSliderTime> = _agoSliderDeviceTime
    private val timeReadCharacteristicHandler = TimeReadCharacteristicHandler(
        _agoSliderDeviceTime
    )

    // ON/OFF
    private var _onOffState: Boolean = true
    private val _agoSliderIsOn = MutableStateFlow(_onOffState)
    val agoSliderIsOn: StateFlow<Boolean> = _agoSliderIsOn
    private val onOffReadCharacteristicHandler = OnOffReadCharacteristicHandler(
        _agoSliderIsOn
    )

    // Manual Brightness
    private var _manualBrightnessState: Byte = 0
    private val _agoSliderManualBrightness = MutableStateFlow(_manualBrightnessState)
    val agoSliderManualBrightness : StateFlow<Byte> = _agoSliderManualBrightness
    private val manualBrightnessReadCharacteristicHandler =
        ManualBrightnessReadCharacteristicHandler(
            _agoSliderManualBrightness
        )

    // Is Automatic Brightness Mode
    private var _isAutoBrightness: Boolean = false
    private val _agoSliderIsAutoBrightness = MutableStateFlow(_isAutoBrightness)
    val agoSliderIsAutoBrightness: StateFlow<Boolean> = _agoSliderIsAutoBrightness
    private val autoBrightnessReadCharacteristicHandler = AutoBrightnessReadCharacteristicHandler(
        _agoSliderIsAutoBrightness
    )

    // Turn ON Alarm
    private var _turnOnAlarm: AgoSliderAlarm = AgoSliderAlarm()
    private val _agoSliderTurnOnAlarm = MutableStateFlow(_turnOnAlarm)
    val agoSliderTurnOnAlarm: StateFlow<AgoSliderAlarm> = _agoSliderTurnOnAlarm
    private val turnOnAlarmReadCharacteristicHandler = TurnOnAlarmReadCharacteristicHandler(
        _agoSliderTurnOnAlarm
    )

    // Turn OFF Alarm
    private var _turnOffAlarm: AgoSliderAlarm = AgoSliderAlarm()
    private val _agoSliderTurnOffAlarm = MutableStateFlow(_turnOffAlarm)
    val agoSliderTurnOffAlarm: StateFlow<AgoSliderAlarm> = _agoSliderTurnOffAlarm
    private val turnOffAlarmReadCharacteristicHandler = TurnOffAlarmReadCharacteristicHandler(
        _agoSliderTurnOffAlarm
    )

    // RTC Aging Offset
    private var _agingOffsetState: Int = 0
    private val _agoSliderAgingOffset  = MutableStateFlow(_agingOffsetState)
    val agoSliderAgingOffset : StateFlow<Int> = _agoSliderAgingOffset
    private val agingOffsetReadCharacteristicHandler = AgingOffsetReadCharacteristicHandler(
        _agoSliderAgingOffset
    )

    // RTC Temperature
    private var _rtcTemperatureState: Float = Float.NaN
    private val _agoSliderRtcTemperature  = MutableStateFlow(_rtcTemperatureState)
    val agoSliderRtcTemperature : StateFlow<Float> = _agoSliderRtcTemperature
    private val rtcTemperatureReadCharacteristicHandler = RtcTemperatureReadCharacteristicHandler(
        _agoSliderRtcTemperature
    )

    // Region: BleManager and Bluetooth internals
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentDevice = MutableStateFlow<AgoSliderDevice?>(null)
    val currentDevice: StateFlow<AgoSliderDevice?> = _currentDevice.asStateFlow()

    private var _lastConnectedDevice : AgoSliderDevice? = null

    private val bleManager: AgoSliderManager = AgoSliderManager(
        bluetoothAdapterProvider.getContext(),
        timeReadCharacteristicHandler,
        onOffReadCharacteristicHandler,
        manualBrightnessReadCharacteristicHandler,
        autoBrightnessReadCharacteristicHandler,
        turnOnAlarmReadCharacteristicHandler,
        turnOffAlarmReadCharacteristicHandler,
        agingOffsetReadCharacteristicHandler,
        rtcTemperatureReadCharacteristicHandler,
        firmwareVersionReadCharacteristicHandler)

    private val connectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Connecting
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Connected
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Ready
            startReadingAllCharacteristics()
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Disconnecting
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Log.e(_tag, "Connection failed with status code, $reason")
            _connectionState.value = ConnectionState.Error("Failed to connect: $reason")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    init {
        bleManager.connectionObserver = connectionObserver
    }

    fun connect(agoSliderDevice: AgoSliderDevice) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) {
            Log.w(_tag, "Already connecting/connected, ignoring new connection request")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        val device = bluetoothAdapterProvider.getAdapter().getRemoteDevice(agoSliderDevice.macAddress)
        bleManager.connect(device)
            .retry(2, 100)
            .useAutoConnect(false)
            .done {
                _currentDevice.value = agoSliderDevice
                _lastConnectedDevice = agoSliderDevice
                Log.i(_tag, "Connection success!")
            }
            .fail { _, status ->
                Log.e(_tag, "Connection failed, $status")
                _connectionState.value = ConnectionState.Error("Connection failed with status code: $status")
            }
            .enqueue()
    }

    fun disconnect() {
        if (_connectionState.value is ConnectionState.Disconnecting ||
            _connectionState.value is ConnectionState.Disconnected) {
            Log.w(_tag, "Already disconnecting/disconnected, ignoring")
            return
        }

        _connectionState.value = ConnectionState.Disconnecting

        bleManager.disconnect()
            .done {
                _currentDevice.value = null
            }
            .fail { _, status ->
                Log.e(_tag, "Disconnection failed with status code: $status")
                _currentDevice.value = null
            }
            .enqueue()
        _currentDevice.value = null
    }

    private var _shouldMaintainConnection = MutableStateFlow(false)

    fun setShouldMaintainConnection(shouldMaintain: Boolean) {
        _shouldMaintainConnection.value = shouldMaintain
        if (!shouldMaintain) {
            disconnect()
        }
    }

    fun tryReconnect() {
        _lastConnectedDevice?.let {
            if (_shouldMaintainConnection.value &&
                _connectionState.value is ConnectionState.Disconnected)
            {
                Log.i(_tag, "Reconnecting to last device: ${it.deviceName}")
                connect(it)
            }
        }
    }

    private fun startReadingAllCharacteristics() {
        bleManager.getTimeCharacteristic()
        bleManager.getOnOffCharacteristic()
        bleManager.getManualBrightnessCharacteristic()
        bleManager.getAutoBrightnessCharacteristic()
        bleManager.getTurnOnAlarmCharacteristic()
        bleManager.getTurnOffAlarmCharacteristic()
        bleManager.getAgingOffsetCharacteristic()
        bleManager.getRtcTemperatureCharacteristic()
        bleManager.getVersionCharacteristic()
    }

    // Region: BLE GATT characteristic setters

    fun setTimeCharacteristic(time: LocalDateTime) {
        _bleDeviceTime = AgoSliderTime(time)
        _agoSliderDeviceTime.value = _bleDeviceTime

        if (bleManager.isReady)
        {
            bleManager.setTimeCharacteristic(_bleDeviceTime)
        }
    }

    fun toggleOnOffCharacteristic() {
        val on = !_agoSliderIsOn.value

        _onOffState = on
        _agoSliderIsOn.value = on

        if (bleManager.isReady) {
            bleManager.setOnOffCharacteristic(on)
        }
    }

    fun setManualBrightnessCharacteristic(brightness: Byte) {
        _manualBrightnessState = brightness
        _agoSliderManualBrightness.value = brightness

        if (bleManager.isReady)
        {
            bleManager.setManualBrightnessCharacteristic(brightness)
        }
    }

    fun setAgingOffsetCharacteristic(agingOffset: Int) {
        _agingOffsetState = agingOffset
        _agoSliderAgingOffset.value = agingOffset

        if (bleManager.isReady) {
            bleManager.setAgingOffsetCharacteristic(agingOffset)
        }
    }

    fun toggleAutoBrightnessCharacteristic() {
        val isAuto = !_agoSliderIsAutoBrightness.value

        _isAutoBrightness = isAuto
        _agoSliderIsAutoBrightness.value = isAuto

        if (bleManager.isReady) {
            bleManager.setAutoBrightnessCharacteristic(isAuto)
        }
    }

    fun setTurnOnAlarmCharacteristic(isActive: Boolean, hours: Byte, minutes: Byte) {
        _turnOnAlarm = AgoSliderAlarm(isActive, hours, minutes)
        _agoSliderTurnOnAlarm.value = _turnOnAlarm

        if (bleManager.isReady)
        {
            bleManager.setTurnOnAlarmCharacteristic(_turnOnAlarm)
        }
    }

    fun setTurnOffAlarmCharacteristic(isActive: Boolean, hours: Byte, minutes: Byte) {
        _turnOffAlarm = AgoSliderAlarm(isActive, hours, minutes)
        _agoSliderTurnOffAlarm.value = _turnOffAlarm

        if (bleManager.isReady)
        {
            bleManager.setTurnOffAlarmCharacteristic(_turnOffAlarm)
        }
    }

    fun setAlarmTime(alarmType: AgoSliderAlarmType, hour: Int, minute: Int, isActive: Boolean) {
        when (alarmType) {
            AgoSliderAlarmType.TURN_ON -> {
                setTurnOnAlarmCharacteristic(
                    isActive,
                    hour.toByte(),
                    minute.toByte())
            }
            AgoSliderAlarmType.TURN_OFF -> {
                setTurnOffAlarmCharacteristic(isActive,
                    hour.toByte(),
                    minute.toByte())
            }
        }
    }

    fun syncBleWithPhone() {
        val mcNow = AgoSliderTime.Companion.now()
        bleManager.setTimeCharacteristic(mcNow)
        _bleDeviceTime = mcNow
        _agoSliderDeviceTime.value = mcNow
    }

    fun toggleAlarmActive(alarmType: AgoSliderAlarmType) {
        when (alarmType) {
            AgoSliderAlarmType.TURN_ON -> {
                val current = _agoSliderTurnOnAlarm.value
                val newAlarm = current.copy(isActive = !current.isActive)
                _agoSliderTurnOnAlarm.value = newAlarm
                bleManager.setTurnOnAlarmCharacteristic(newAlarm)
            }
            AgoSliderAlarmType.TURN_OFF -> {
                val current = _agoSliderTurnOffAlarm.value
                val newAlarm = current.copy(isActive = !current.isActive)
                _agoSliderTurnOffAlarm.value = newAlarm
                bleManager.setTurnOffAlarmCharacteristic(newAlarm)
            }
        }
    }

    // Region: OTA Update

    suspend fun getCurrentVersion(): String {
        return withTimeout(5000L) {
            _versionReadEvent.value = null
            bleManager.getVersionCharacteristic()

            _versionReadEvent
                .filterNotNull()
                .first()

            _agoSliderFirmwareVersion.value
        }
    }

    suspend fun setOtaControlCharacteristic(command: ByteArray): Boolean {
        return suspendCancellableCoroutine { continuation ->
            bleManager.setOtaControlCharacteristic(command) { success ->
                if (success) {
                    continuation.resume(true)
                } else {
                    continuation.resume(false)
                }
            }
        }
    }

    suspend fun setOtaDataCharacteristic(data: ByteArray): Boolean {
        return suspendCancellableCoroutine { continuation ->
            bleManager.setOtaDataCharacteristic(data) { success ->
                if (success) {
                    continuation.resume(true)
                } else {
                    continuation.resume(false)
                }
            }
        }
    }
}