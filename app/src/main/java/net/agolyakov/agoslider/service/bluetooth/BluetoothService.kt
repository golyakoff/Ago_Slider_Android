package net.agolyakov.agoslider.service.bluetooth

import android.bluetooth.BluetoothDevice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.ble.ConnectionState
import net.agolyakov.agoslider.data.model.ble.HomeStatus
import net.agolyakov.agoslider.service.bluetooth.handlers.*
import no.nordicsemi.android.ble.observer.ConnectionObserver
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BluetoothService @Inject constructor(
    private val bluetoothAdapterProvider: BluetoothAdapterProvider
) {
    private val tag = "BluetoothService"

    // OTA mode flag (keep existing logic)
    private var isOtaUpdateMode = false

    fun enterOtaUpdateMode() {
        isOtaUpdateMode = true
        Log.d(tag, "Entered OTA update mode - connections will be preserved")
    }

    fun exitOtaUpdateMode() {
        isOtaUpdateMode = false
        Log.d(tag, "Exited OTA update mode")
    }

    fun shouldPreserveConnection(): Boolean = isOtaUpdateMode

    // ========================== State Flows ==========================

    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentDevice = MutableStateFlow<AgoSliderDevice?>(null)
    val currentDevice: StateFlow<AgoSliderDevice?> = _currentDevice.asStateFlow()

    private var _lastConnectedDevice: AgoSliderDevice? = null
    private var _shouldMaintainConnection = false

    // Firmware version
    private val _firmwareVersion = MutableStateFlow("Unknown")
    val firmwareVersion: StateFlow<String> = _firmwareVersion
    private val _versionReadEvent = MutableStateFlow<Unit?>(null)

    // Motor enable
    private val _motorsEnabled = MutableStateFlow(false)
    val motorsEnabled: StateFlow<Boolean> = _motorsEnabled

    // Home status
    private val _homeStatus = MutableStateFlow(HomeStatus(Triple(false, false, false), Triple(false, false, false)))
    val homeStatus: StateFlow<HomeStatus> = _homeStatus

    // Limit switches
    private val _limitStatus = MutableStateFlow(Triple(false, false, false))
    val limitStatus: StateFlow<Triple<Boolean, Boolean, Boolean>> = _limitStatus

    // Battery level (0-255 raw, convert to percent if needed)
    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    // Power info (voltage, current, power)
    private val _powerInfo = MutableStateFlow(Triple(0f, 0f, 0f))
    val powerInfo: StateFlow<Triple<Float, Float, Float>> = _powerInfo

    // Power info string
    private val _powerInfoString = MutableStateFlow("")
    val powerInfoString: StateFlow<String> = _powerInfoString

    // Microsteps (0=256,1=1,2=2,4,8,16,32,64,128) – stored as integer values
    private val _microsteps = MutableStateFlow(Triple(16, 16, 16))
    val microsteps: StateFlow<Triple<Int, Int, Int>> = _microsteps

    // Run current (mA)
    private val _runCurrent = MutableStateFlow(Triple(0, 0, 0))
    val runCurrent: StateFlow<Triple<Int, Int, Int>> = _runCurrent

    // Hold current (mA)
    private val _holdCurrent = MutableStateFlow(Triple(0, 0, 0))
    val holdCurrent: StateFlow<Triple<Int, Int, Int>> = _holdCurrent

    // Axis unit (true = degrees, false = mm)
    private val _axisUnit = MutableStateFlow(Triple(false, false, false))
    val axisUnit: StateFlow<Triple<Boolean, Boolean, Boolean>> = _axisUnit

    // Units per step (mm/step or deg/step)
    private val _unitsPerStep = MutableStateFlow(Triple(0f, 0f, 0f))
    val unitsPerStep: StateFlow<Triple<Float, Float, Float>> = _unitsPerStep

    // Axis speed (steps/sec)
    private val _axisSpeed = MutableStateFlow(Triple(0, 0, 0))
    val axisSpeed: StateFlow<Triple<Int, Int, Int>> = _axisSpeed

    // Axis acceleration (steps/sec²)
    private val _axisAccel = MutableStateFlow(Triple(0, 0, 0))
    val axisAccel: StateFlow<Triple<Int, Int, Int>> = _axisAccel

    // Virtual limit enable
    private val _virtualLimit = MutableStateFlow(Triple(false, false, false))
    val virtualLimit: StateFlow<Triple<Boolean, Boolean, Boolean>> = _virtualLimit

    // StealthChop enable
    private val _stealthChop = MutableStateFlow(Triple(false, false, false))
    val stealthChop: StateFlow<Triple<Boolean, Boolean, Boolean>> = _stealthChop

    // Invert direction
    private val _invertDir = MutableStateFlow(Triple(false, false, false))
    val invertDir: StateFlow<Triple<Boolean, Boolean, Boolean>> = _invertDir

    // ========================== Handlers ==========================
    private val versionHandler = VersionReadCharacteristicHandler(_firmwareVersion, _versionReadEvent)

    private val motEnHandler = MotEnReadCharacteristicHandler(_motorsEnabled)
    private val homeHandler = HomeReadCharacteristicHandler(_homeStatus)
    private val limitHandler = LimitReadCharacteristicHandler(_limitStatus)
    private val batteryLevelHandler = BatteryLevelReadCharacteristicHandler(_batteryLevel)
    private val powerInfoHandler = PowerInfoReadCharacteristicHandler(_powerInfo)
    private val powerInfoStringHandler = PowerInfoStringReadCharacteristicHandler(_powerInfoString)

    private val microstepsHandler = MicrostepsReadCharacteristicHandler(_microsteps)
    private val runCurrentHandler = RunCurrentReadCharacteristicHandler(_runCurrent)
    private val holdCurrentHandler = HoldCurrentReadCharacteristicHandler(_holdCurrent)
    private val axisUnitHandler = AxisUnitReadCharacteristicHandler(_axisUnit)
    private val unitsPerStepHandler = UnitsPerStepReadCharacteristicHandler(_unitsPerStep)
    private val axisSpeedHandler = AxisSpeedReadCharacteristicHandler(_axisSpeed)
    private val axisAccelHandler = AxisAccelReadCharacteristicHandler(_axisAccel)
    private val virtualLimitHandler = VirtualLimitReadCharacteristicHandler(_virtualLimit)
    private val stealthChopHandler = StealthChopReadCharacteristicHandler(_stealthChop)
    private val invertDirHandler = InvertDirReadCharacteristicHandler(_invertDir)

    // ========================== BLE Manager ==========================
    private val bleManager = AgoSliderManager(
        bluetoothAdapterProvider.getContext(),
        microstepsHandler,
        runCurrentHandler,
        holdCurrentHandler,
        axisUnitHandler,
        unitsPerStepHandler,
        axisSpeedHandler,
        axisAccelHandler,
        virtualLimitHandler,
        stealthChopHandler,
        invertDirHandler,
        batteryLevelHandler,
        powerInfoHandler,
        powerInfoStringHandler,
        limitHandler,
        homeHandler,
        motEnHandler,
        versionHandler
    )

    private val connectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Connecting
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Connected
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Ready
            readAllConfigurationCharacteristics()
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Disconnecting
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Log.e(tag, "Connection failed with status code $reason")
            _connectionState.value = ConnectionState.Error("Failed to connect: $reason")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    init {
        bleManager.connectionObserver = connectionObserver
    }

    fun connect(device: AgoSliderDevice) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) {
            Log.w(tag, "Already connecting/connected, ignoring")
            return
        }
        _connectionState.value = ConnectionState.Connecting
        val btDevice = bluetoothAdapterProvider.getAdapter().getRemoteDevice(device.macAddress)
        bleManager.connect(btDevice)
            .retry(2, 100)
            .useAutoConnect(false)
            .done {
                _currentDevice.value = device
                _lastConnectedDevice = device
                Log.i(tag, "Connected successfully")
            }
            .fail { _, status ->
                Log.e(tag, "Connection failed, status=$status")
                _connectionState.value = ConnectionState.Error("Connection failed with status: $status")
            }
            .enqueue()
    }

    fun disconnect() {
        if (_connectionState.value is ConnectionState.Disconnecting ||
            _connectionState.value is ConnectionState.Disconnected) return
        _connectionState.value = ConnectionState.Disconnecting
        bleManager.disconnect()
            .done {
                _currentDevice.value = null
            }
            .fail { _, status ->
                Log.e(tag, "Disconnect failed: $status")
                _currentDevice.value = null
            }
            .enqueue()
    }

    private fun readAllConfigurationCharacteristics() {
        bleManager.readMicrostepsCharacteristic()
        bleManager.readRunCurrentCharacteristic()
        bleManager.readHoldCurrentCharacteristic()
        bleManager.readAxisUnitCharacteristic()
        bleManager.readUnitsPerStepCharacteristic()
        bleManager.readAxisSpeedCharacteristic()
        bleManager.readAxisAccelCharacteristic()
        bleManager.readVirtualLimitCharacteristic()
        bleManager.readStealthChopCharacteristic()
        bleManager.readInvertDirCharacteristic()
        bleManager.readVersionCharacteristic()
        // MOT_EN, LIMIT, BATT and power are notified only when they change, which may not
        // happen for a long time after connecting, so read their current state once.
        bleManager.readMotEnCharacteristic()
        bleManager.readLimitCharacteristic()
        bleManager.readBattLevelCharacteristic()
        bleManager.readPowerInfoCharacteristic()
        bleManager.readPowerInfoStringCharacteristic()
        // Notifications for MOT_EN, HOME, LIMIT, BATT, POWER, VERSION are set up in initialize()
    }

    // ========================== Public Write Methods ==========================

    fun setMotorsEnabled(enabled: Boolean) {
        bleManager.writeMotEnCharacteristic(enabled)
        bleManager.readMotEnCharacteristic()
    }

    fun sendHomeCommand(homeX: Boolean, homeC: Boolean, homeB: Boolean) {
        bleManager.writeHomeCommand(homeX, homeC, homeB)
    }

    fun sendMoveCommand(x: Int, c: Int, b: Int) {
        bleManager.writeMoveCommand(x, c, b)
    }

    fun setMicrosteps(x: Int, c: Int, b: Int) {
        // Convert Int to Byte according to encoding: 256 -> 0, otherwise the value itself
        fun encode(value: Int): Byte = if (value == 256) 0 else value.toByte()
        bleManager.writeMicrostepsCharacteristic(encode(x), encode(c), encode(b))
        bleManager.readMicrostepsCharacteristic()
    }

    fun setRunCurrent(x: Int, c: Int, b: Int) {
        bleManager.writeRunCurrentCharacteristic(x, c, b)
        bleManager.readRunCurrentCharacteristic()
        bleManager.readHoldCurrentCharacteristic()
    }

    fun setHoldCurrent(x: Int, c: Int, b: Int) {
        bleManager.writeHoldCurrentCharacteristic(x, c, b)
        bleManager.readHoldCurrentCharacteristic()
    }

    fun setAxisUnit(xDeg: Boolean, cDeg: Boolean, bDeg: Boolean) {
        bleManager.writeAxisUnitCharacteristic(xDeg, cDeg, bDeg)
        bleManager.readAxisUnitCharacteristic()
    }

    fun setUnitsPerStep(x: Float, c: Float, b: Float) {
        bleManager.writeUnitsPerStepCharacteristic(x, c, b)
        bleManager.readUnitsPerStepCharacteristic()
    }

    fun setAxisSpeed(x: Int, c: Int, b: Int) {
        bleManager.writeAxisSpeedCharacteristic(x, c, b)
        bleManager.readAxisSpeedCharacteristic()
    }

    fun setAxisAccel(x: Int, c: Int, b: Int) {
        bleManager.writeAxisAccelCharacteristic(x, c, b)
        bleManager.readAxisAccelCharacteristic()
    }

    fun setVirtualLimit(xEnable: Boolean, cEnable: Boolean, bEnable: Boolean) {
        bleManager.writeVirtualLimitCharacteristic(xEnable, cEnable, bEnable)
        bleManager.readVirtualLimitCharacteristic()
    }

    fun setStealthChop(xEnable: Boolean, cEnable: Boolean, bEnable: Boolean) {
        bleManager.writeStealthChopCharacteristic(xEnable, cEnable, bEnable)
        bleManager.readStealthChopCharacteristic()
    }

    fun setInvertDir(xInvert: Boolean, cInvert: Boolean, bInvert: Boolean) {
        bleManager.writeInvertDirCharacteristic(xInvert, cInvert, bInvert)
        bleManager.readInvertDirCharacteristic()
    }

    // ========================== OTA Methods ==========================

    suspend fun getCurrentVersion(): String {
        return withTimeout(5000L) {
            _versionReadEvent.value = null
            bleManager.readVersionCharacteristic()
            _versionReadEvent.filterNotNull().first()
            _firmwareVersion.value
        }
    }

    suspend fun sendOtaControl(command: ByteArray): Boolean {
        return suspendCancellableCoroutine { continuation ->
            bleManager.writeOtaControlCharacteristic(command) { success ->
                continuation.resume(success)
            }
        }
    }

    suspend fun sendOtaData(data: ByteArray): Boolean {
        return suspendCancellableCoroutine { continuation ->
            bleManager.writeOtaDataCharacteristic(data) { success ->
                continuation.resume(success)
            }
        }
    }

    suspend fun getNegotiatedMtu(): Int = bleManager.awaitNegotiatedMtu()

    // ========================== Connection Management ==========================

    fun setShouldMaintainConnection(shouldMaintain: Boolean) {
        _shouldMaintainConnection = shouldMaintain
        if (!shouldMaintain) disconnect()
    }

    fun tryReconnect() {
        _lastConnectedDevice?.let {
            if (_shouldMaintainConnection && _connectionState.value is ConnectionState.Disconnected) {
                connect(it)
            }
        }
    }
}