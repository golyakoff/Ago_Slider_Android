package net.agolyakov.agoslider.service.bluetooth

import android.bluetooth.BluetoothDevice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.ble.ConnectionState
import net.agolyakov.agoslider.data.model.ble.HomeStatus
import net.agolyakov.agoslider.data.model.position.DeviceCalibStatus
import net.agolyakov.agoslider.data.model.position.DevicePosition
import net.agolyakov.agoslider.data.model.power.PowerSample
import net.agolyakov.agoslider.data.model.scenario.ScenarioStatus
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

    // Limit switches: current state plus monotonic per-axis trigger counters (the state flow
    // conflates a trigger-and-release that happens between collector resumptions; the
    // counters make every hit observable)
    private val _limitStatus = MutableStateFlow(Triple(false, false, false))
    val limitStatus: StateFlow<Triple<Boolean, Boolean, Boolean>> = _limitStatus
    private val _limitHitCounts = MutableStateFlow(Triple(0, 0, 0))
    val limitHitCounts: StateFlow<Triple<Int, Int, Int>> = _limitHitCounts

    // Device-reported position in STEP pulses (firmware >= 0.1.4; the firmware zeroes an
    // axis when it completes homing). Null until the first value arrives — which doubles
    // as "this firmware does not support POSITION".
    private val _devicePosition = MutableStateFlow<DevicePosition?>(null)
    val devicePosition: StateFlow<DevicePosition?> = _devicePosition

    // Hardware-calibration status notifications (firmware >= 0.1.4); null until one arrives
    private val _calibStatus = MutableStateFlow<DeviceCalibStatus?>(null)
    val calibStatus: StateFlow<DeviceCalibStatus?> = _calibStatus

    // Status of a scenario running on the device. Read once on every connect, then notified:
    // the run belongs to the slider, so this is how the app finds out what happened while it
    // was away — or was not even running.
    private val _scenarioStatus = MutableStateFlow<ScenarioStatus?>(null)
    val scenarioStatus: StateFlow<ScenarioStatus?> = _scenarioStatus

    // Battery level (0-255 raw, convert to percent if needed)
    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    // Power info (voltage, current, power)
    private val _powerInfo = MutableStateFlow(Triple(0f, 0f, 0f))
    val powerInfo: StateFlow<Triple<Float, Float, Float>> = _powerInfo

    // Power info string
    private val _powerInfoString = MutableStateFlow("")
    val powerInfoString: StateFlow<String> = _powerInfoString

    // Power readings kept for the duration of a connection, so the Service tab can plot how
    // voltage, current and power moved. Trimmed to the last POWER_HISTORY_MS and cleared on
    // every new connection — this is a live session view, not stored telemetry.
    private val _powerHistory = MutableStateFlow<List<PowerSample>>(emptyList())
    val powerHistory: StateFlow<List<PowerSample>> = _powerHistory

    private fun recordPowerSample(volts: Float, amperes: Float, watts: Float) {
        val now = System.currentTimeMillis()
        val cutoff = now - POWER_HISTORY_MS
        _powerHistory.value = (_powerHistory.value + PowerSample(now, volts, amperes, watts))
            .dropWhile { it.timestampMs < cutoff }
    }

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
    private val limitHandler = LimitReadCharacteristicHandler(_limitStatus, _limitHitCounts)
    private val positionHandler = PositionReadCharacteristicHandler(_devicePosition)
    private val calibStatusHandler = CalibStatusReadCharacteristicHandler(_calibStatus)
    private val scenarioStatusHandler = ScenarioStatusReadCharacteristicHandler(_scenarioStatus)
    private val batteryLevelHandler = BatteryLevelReadCharacteristicHandler(_batteryLevel)
    private val powerInfoHandler = PowerInfoReadCharacteristicHandler(_powerInfo, ::recordPowerSample)
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
        positionHandler,
        calibStatusHandler,
        scenarioStatusHandler,
        homeHandler,
        motEnHandler,
        versionHandler
    )

    private val connectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Connecting
            // A new session starts its own history; the previous device's readings would only
            // show up as a gap of unknown length on the chart
            _powerHistory.value = emptyList()
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
            // Support for the optional POSITION characteristic is unknown until reconnect
            _devicePosition.value = null
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        bleManager.connectionObserver = connectionObserver
        // The device samples power every 2 s but only notifies once a reading moves past a
        // threshold, so on a steady supply the chart would have nothing to draw. Repeat the
        // last known reading on a timer to keep the series continuous; notifications still
        // land immediately, and this only fills the quiet stretches between them.
        serviceScope.launch {
            while (true) {
                delay(POWER_SAMPLE_INTERVAL_MS)
                if (_connectionState.value !is ConnectionState.Ready) continue
                val last = _powerHistory.value.lastOrNull()
                if (last == null ||
                    System.currentTimeMillis() - last.timestampMs >= POWER_SAMPLE_INTERVAL_MS
                ) {
                    val (volts, amperes, watts) = _powerInfo.value
                    recordPowerSample(volts, amperes, watts)
                }
            }
        }
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
        bleManager.readPositionCharacteristic()
        bleManager.readScenarioCharacteristic()
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

    /** Reset the last calibration status so a new run cannot react to a stale terminal one. */
    fun resetCalibStatus() {
        _calibStatus.value = null
    }

    fun sendCalibrateCommand(axis: Int, parkOffsetSteps: Int, retreatSteps: Int): Boolean =
        bleManager.writeCalibrateCommand(axis, parkOffsetSteps, retreatSteps)

    fun sendCalibrateAbort(): Boolean = bleManager.writeCalibrateAbort()

    fun scenarioSupported(): Boolean = bleManager.hasScenarioSupport()

    fun sendScenarioStart(scenarioId: Int, durationMs: Long, payload: ByteArray): Boolean =
        bleManager.writeScenarioStart(scenarioId, durationMs, payload)

    fun sendScenarioStop(): Boolean = bleManager.writeScenarioStop()

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

    companion object {
        /** How far back the session power chart reaches. */
        const val POWER_HISTORY_MS = 30 * 60 * 1000L

        /** Longest gap the chart tolerates before repeating the last reading. */
        const val POWER_SAMPLE_INTERVAL_MS = 5_000L
    }
}