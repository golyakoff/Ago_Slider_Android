package net.agolyakov.agoslider.service.position

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.agolyakov.agoslider.data.local.PositioningPreferences
import net.agolyakov.agoslider.data.model.ble.ConnectionState
import net.agolyakov.agoslider.data.model.position.AxisCoordinates
import net.agolyakov.agoslider.data.model.position.CalibrationPhase
import net.agolyakov.agoslider.data.model.position.CalibrationState
import net.agolyakov.agoslider.data.model.position.DeviceCalibStatus
import net.agolyakov.agoslider.data.model.position.PositioningSettings
import net.agolyakov.agoslider.service.bluetooth.BluetoothService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Virtual coordinates for the three axes, CNC-style, kept entirely on the phone.
 *
 * The firmware neither reports a position nor has encoders — its own idea of where an axis is
 * would be the same open-loop count of commanded steps this class keeps. The count is therefore
 * exactly as good as the firmware's as long as no move is cut short, which only happens at a
 * limit switch; any unexpected switch hit (and anything else that breaks the steps-to-units
 * mapping) simply invalidates the axis until it is homed again.
 *
 * All moves and homing must go through this class, not [BluetoothService] directly, so that
 * every commanded step is counted and soft limits are applied.
 */
@Singleton
class PositionManager @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val positioningPreferences: PositioningPreferences
) {
    private val tag = "PositionManager"

    // Nordic BLE callbacks arrive on the main thread; keeping all state mutation there too
    // makes the counters race-free without locks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Commanded position in STEP pulses (microsteps), origin at the homed position + offset
    private val stepCounts = intArrayOf(0, 0, 0)
    private val validAxes = booleanArrayOf(false, false, false)

    private val _coordinates = MutableStateFlow(AxisCoordinates.UNKNOWN)
    val coordinates: StateFlow<AxisCoordinates> = _coordinates.asStateFlow()

    private val _settings = MutableStateFlow(PositioningSettings.DEFAULT)
    val settings: StateFlow<PositioningSettings> = _settings.asStateFlow()

    private val _calibration = MutableStateFlow(CalibrationState())
    val calibration: StateFlow<CalibrationState> = _calibration.asStateFlow()

    // Axes whose limit switch is expected to trigger (homing or calibration in progress) —
    // a hit on any other axis means the count can no longer be trusted
    private val protectedAxes = mutableSetOf<Int>()

    // Axes homed via startHoming() that still owe the home offset move once homed
    private val pendingHomeOffset = mutableSetOf<Int>()
    private val homeGeneration = intArrayOf(0, 0, 0)

    private var calibrationJob: Job? = null

    init {
        // Settings follow the connected device
        scope.launch {
            bluetoothService.currentDevice.collect { device ->
                _settings.value = device?.let { positioningPreferences.get(it.macAddress) }
                    ?: PositioningSettings.DEFAULT
            }
        }

        // Losing the link stops any calibration but does not cost the coordinate: the device
        // holds the axes, remembers that they are homed, and says so in POSITION, so a
        // reconnect restores validity instead of demanding a fresh homing run.
        scope.launch {
            bluetoothService.connectionState.collect { state ->
                val lost = state is ConnectionState.Disconnected ||
                        state is ConnectionState.Disconnecting ||
                        state is ConnectionState.Error
                if (lost) calibrationJob?.cancel()
            }
        }
        scope.launch {
            bluetoothService.motorsEnabled.collect { enabled ->
                if (!enabled) {
                    calibrationJob?.cancel()
                    invalidateAll()
                }
            }
        }

        // Any change to the steps-to-units geometry makes the counted steps meaningless
        scope.launch { bluetoothService.microsteps.drop(1).collect { invalidateAll() } }
        scope.launch { bluetoothService.unitsPerStep.drop(1).collect { invalidateAll() } }
        scope.launch { bluetoothService.invertDir.drop(1).collect { invalidateAll() } }

        // Once EVERY axis of the run reports homed, drive them off their endstops by the home
        // offsets — those positions become coordinate 0.
        //
        // Waiting for the whole batch is the point: the firmware refuses a MOVE while its
        // homing run is active, and a run covering several axes stays active until the last
        // one arrives. Sending an axis its offset the moment that axis finished meant the
        // early finishers' moves were silently dropped, and "home all" left X and B sitting
        // on their switches while only the last axis reached zero.
        scope.launch {
            bluetoothService.homeStatus.collect { status ->
                if (pendingHomeOffset.isEmpty()) return@collect
                val batch = pendingHomeOffset.toList()
                if (batch.any { !status.homed.at(it) }) return@collect
                pendingHomeOffset.clear()
                applyHomeOffsets(batch)
            }
        }

        // Firmware >= 0.1.4 reports its own position (zeroed at the switch on homing) —
        // the same open-loop step count, but kept at the source, so force stops and homing
        // are reflected exactly. It continuously overwrites the locally counted frame; the
        // app's zero sits homeOffset above the firmware's. The local commanded increments
        // remain as optimistic estimates between 200 ms notifications. On older firmware
        // the flow stays null and nothing changes.
        scope.launch {
            bluetoothService.devicePosition.collect { fw ->
                if (fw == null) return@collect
                for (axis in 0..2) {
                    stepCounts[axis] =
                        fw.steps.at(axis) - unitsToSteps(_settings.value.homeOffset.at(axis), axis)
                }
                // The device says outright whether an axis is still anchored to its endstop,
                // and that answer outlives the BLE link: a reconnect restores the coordinate
                // instead of demanding a fresh homing run, while a device that rebooted comes
                // back with the flags clear, so its zero can never pass for a homed one.
                for (axis in 0..2) {
                    if (!fw.homeValid.at(axis)) {
                        if (validAxes[axis]) invalidateAxis(axis)
                    } else if (!validAxes[axis] && !pendingHomeOffset.contains(axis)) {
                        validAxes[axis] = true
                    }
                }
                publish()
            }
        }
    }

    // ========================== Commands ==========================

    /** Home the selected axes; coordinate 0 is established automatically when each finishes. */
    fun startHoming(homeX: Boolean, homeC: Boolean, homeB: Boolean) {
        val requested = listOf(homeX, homeC, homeB)
        for (axis in 0..2) {
            if (!requested[axis]) continue
            invalidateAxis(axis)
            protectedAxes.add(axis)
            pendingHomeOffset.add(axis)
            // The firmware gives up homing after 90 s; if that happens no homed notification
            // ever comes, so drop the pending offset instead of applying it to a later homing
            val generation = ++homeGeneration[axis]
            scope.launch {
                delay(HOMING_TIMEOUT_MS)
                if (homeGeneration[axis] == generation && pendingHomeOffset.remove(axis)) {
                    protectedAxes.remove(axis)
                }
            }
        }
        bluetoothService.sendHomeCommand(homeX, homeC, homeB)
    }

    /**
     * Relative move in each axis's own unit. For a valid axis whose virtual limit is enabled
     * the distance is clamped so the commanded position stays within [min, max]; an axis that
     * has not been homed moves unclamped and stays unhomed.
     */
    fun moveRelative(x: Float, c: Float, b: Float) {
        val requested = floatArrayOf(x, c, b)
        val limitEnabled = bluetoothService.virtualLimit.value
        val settings = _settings.value
        val steps = IntArray(3)
        for (axis in 0..2) {
            var distance = requested[axis]
            if (validAxes[axis] && limitEnabled.at(axis)) {
                val position = stepsToUnits(stepCounts[axis], axis)
                val target = (position + distance)
                    .coerceIn(settings.limitMin.at(axis), settings.limitMax.at(axis))
                distance = target - position
            }
            steps[axis] = unitsToSteps(distance, axis)
        }
        if (steps.all { it == 0 }) return
        bluetoothService.sendMoveCommand(steps[0], steps[1], steps[2])
        for (axis in 0..2) {
            if (validAxes[axis]) stepCounts[axis] += steps[axis]
        }
        publish()
    }

    fun saveSettings(newSettings: PositioningSettings) {
        _settings.value = newSettings
        bluetoothService.currentDevice.value?.let {
            positioningPreferences.save(it.macAddress, newSettings)
        }
    }

    // ========================== Calibration ==========================

    /**
     * Hardware calibration: the whole endstop dance (fast seek to min, retreat, slow
     * re-seek anchoring the device position at 0, same at the max end, park at the home
     * offset) runs INSIDE the firmware, which reacts to endstop events with ~1 ms latency —
     * the sensors emit millisecond blinks no BLE-driven loop can catch in time. The app only
     * sends the command, mirrors the reported phases and records the measured span.
     */
    fun startCalibration(axis: Int) {
        if (calibrationJob?.isActive == true) return
        calibrationJob = scope.launch {
            try {
                runCalibration(axis)
            } catch (e: CancellationException) {
                _calibration.value = CalibrationState(axis, CalibrationPhase.IDLE)
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Calibration failed", e)
                _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            } finally {
                protectedAxes.remove(axis)
            }
        }
    }

    fun cancelCalibration() {
        if (calibrationJob?.isActive != true) return
        val axis = _calibration.value.axis
        calibrationJob?.cancel()
        // The device runs the sequence autonomously — tell it to stop (force stop inside)
        bluetoothService.sendCalibrateAbort()
        axis?.let { invalidateAxis(it) }
    }

    private suspend fun runCalibration(axis: Int) {
        val degrees = bluetoothService.axisUnit.value.at(axis)
        val coarse = if (degrees) COARSE_QUANTUM_DEG else COARSE_QUANTUM_MM
        val offsetUnits = _settings.value.homeOffset.at(axis)

        Log.i(tag, "Calibration axis=$axis start (hardware): offset=$offsetUnits settings=${_settings.value}")
        val ready = bluetoothService.connectionState.value is ConnectionState.Ready &&
                bluetoothService.motorsEnabled.value &&
                unitsToSteps(coarse, axis) > 0 // steps-to-units geometry must be known
        if (!ready) {
            Log.w(tag, "Calibration axis=$axis not ready to start")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        protectedAxes.add(axis)
        invalidateAxis(axis)

        // A stale terminal status from an earlier run must not satisfy this run's wait
        bluetoothService.resetCalibStatus()
        val parkSteps = unitsToSteps(offsetUnits, axis)
        val retreatSteps = unitsToSteps(RETREAT_COARSE_QUANTA * coarse, axis)
        if (!bluetoothService.sendCalibrateCommand(axis, parkSteps, retreatSteps)) {
            Log.w(tag, "Calibration axis=$axis: the device refused the CALIBRATE write")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        // Mirror the device's phases and wait for a terminal one
        val terminal = withTimeoutOrNull(CALIB_TOTAL_TIMEOUT_MS) {
            bluetoothService.calibStatus
                .filterNotNull()
                .filter { it.axis == axis }
                .onEach { _calibration.value = CalibrationState(axis, mapDevicePhase(it.phase)) }
                .first {
                    it.phase == DeviceCalibStatus.PHASE_DONE ||
                            it.phase == DeviceCalibStatus.PHASE_FAILED
                }
        }
        if (terminal == null) {
            Log.w(tag, "Calibration axis=$axis: no terminal status from the device, aborting")
            bluetoothService.sendCalibrateAbort()
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }
        if (terminal.phase == DeviceCalibStatus.PHASE_FAILED) {
            Log.w(tag, "Calibration axis=$axis: device reported failure")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        // Let the parked position land before trusting the frame
        delay(FW_POSITION_SYNC_MS)
        val spanUnits = stepsToUnits(terminal.spanSteps, axis)
        // A rotary axis is centred on its zero: the useful travel is the same either side, so
        // its limits are half the measured span in each direction. A linear axis is not — the
        // rail's zero sits just clear of the near endstop, so its range runs from minus the
        // park offset up to whatever is left of the span.
        val rotary = bluetoothService.axisUnit.value.at(axis)
        val measuredMin = if (rotary) -spanUnits / 2f else -offsetUnits
        val measuredMax = if (rotary) spanUnits / 2f else spanUnits - offsetUnits
        if (spanUnits <= coarse) {
            Log.w(tag, "Calibration axis=$axis: span=$spanUnits is not plausible")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        // The device parked at the home offset, i.e. at app coordinate 0
        validAxes[axis] = true
        publish()

        val settings = _settings.value
        saveSettings(
            settings.copy(
                limitMin = settings.limitMin.with(axis, measuredMin),
                limitMax = settings.limitMax.with(axis, measuredMax)
            )
        )
        _calibration.value = CalibrationState(axis, CalibrationPhase.DONE)
        Log.i(tag, "Axis $axis calibrated (hardware): span=$spanUnits rotary=$rotary " +
            "min=$measuredMin max=$measuredMax, parked at 0")
    }

    private fun mapDevicePhase(phase: Int): CalibrationPhase = when (phase) {
        DeviceCalibStatus.PHASE_SEEK_MIN_FAST,
        DeviceCalibStatus.PHASE_SEEK_MIN_SLOW -> CalibrationPhase.HOMING
        DeviceCalibStatus.PHASE_RETREAT_MIN -> CalibrationPhase.CLEARING
        DeviceCalibStatus.PHASE_SEEK_MAX_FAST -> CalibrationPhase.MEASURING
        DeviceCalibStatus.PHASE_RETREAT_MAX -> CalibrationPhase.BACKOFF
        DeviceCalibStatus.PHASE_SEEK_MAX_SLOW -> CalibrationPhase.FINE
        DeviceCalibStatus.PHASE_PARK -> CalibrationPhase.OFFSET
        DeviceCalibStatus.PHASE_DONE -> CalibrationPhase.DONE
        else -> CalibrationPhase.FAILED
    }

    // ========================== Home offset ==========================

    private fun applyHomeOffsets(axes: List<Int>) {
        scope.launch {
            // Same firmware homing-flag race as in runCalibration — see the comment there
            delay(HOMED_SETTLE_MS)
            val steps = IntArray(3)
            for (axis in axes) {
                steps[axis] = unitsToSteps(_settings.value.homeOffset.at(axis), axis)
            }
            Log.d(tag, "Applying home offsets on axes $axes: ${steps.toList()}")
            if (steps.any { it != 0 }) {
                // One command for all of them: the axes are independent, and moving them
                // together is both quicker and free of the ordering the firmware dislikes
                bluetoothService.sendMoveCommand(steps[0], steps[1], steps[2])
                delay(axes.maxOf { moveDurationMs(steps[it], it) })
            }
            for (axis in axes) {
                stepCounts[axis] = 0
                validAxes[axis] = true
                protectedAxes.remove(axis)
            }
            publish()
        }
    }

    // ========================== Helpers ==========================

    private fun sendAxisMove(axis: Int, steps: Int) {
        bluetoothService.sendMoveCommand(
            if (axis == 0) steps else 0,
            if (axis == 1) steps else 0,
            if (axis == 2) steps else 0
        )
    }

    /**
     * Estimate of a move's duration from the configured speed and acceleration. There is no
     * move-completed notification in the protocol, so waits are time-based.
     */
    private fun moveDurationMs(steps: Int, axis: Int): Long {
        val speed = realSpeedStepsPerSec(axis)
        val accel = bluetoothService.axisAccel.value.at(axis).coerceAtLeast(1).toFloat()
        val distance = abs(steps).toFloat()
        val rampDistance = speed * speed / accel // accelerating plus decelerating
        val seconds = if (distance >= rampDistance) {
            distance / speed + speed / accel     // trapezoid: cruise plus the two ramps
        } else {
            2f * sqrt(distance / accel)          // triangle: cruise speed never reached
        }
        return (seconds * 1000).toLong() + MOVE_SETTLE_MS
    }

    /** The device runs at the speed it is given, so the setting needs no correction. */
    private fun realSpeedStepsPerSec(axis: Int): Float =
        bluetoothService.axisSpeed.value.at(axis).coerceAtLeast(10).toFloat()

    /** The device moves in STEP pulses (microsteps) while `units per step` is per full step. */
    /** STEP pulses for a distance in an axis's own units — scenarios speak to the device in
     *  pulses, so they need the same conversion the move commands use. */
    fun unitsToStepsPublic(units: Float, axis: Int): Int = unitsToSteps(units, axis)

    /** Move an axis to an absolute virtual coordinate, honouring the same soft limits. */
    fun moveAxisTo(axis: Int, target: Float) {
        val current = _coordinates.value.units.at(axis)
        val delta = target - current
        when (axis) {
            0 -> moveRelative(delta, 0f, 0f)
            1 -> moveRelative(0f, delta, 0f)
            else -> moveRelative(0f, 0f, delta)
        }
    }

    private fun unitsToSteps(units: Float, axis: Int): Int {
        val unitsPerStep = bluetoothService.unitsPerStep.value.at(axis)
        if (unitsPerStep == 0f) return 0
        val microsteps = bluetoothService.microsteps.value.at(axis)
        return (units / unitsPerStep * microsteps).roundToInt()
    }

    private fun stepsToUnits(steps: Int, axis: Int): Float {
        val microsteps = bluetoothService.microsteps.value.at(axis)
        if (microsteps == 0) return 0f
        return steps.toFloat() / microsteps * bluetoothService.unitsPerStep.value.at(axis)
    }

    private fun invalidateAxis(axis: Int) {
        validAxes[axis] = false
        publish()
    }

    private fun invalidateAll() {
        validAxes.fill(false)
        pendingHomeOffset.clear()
        protectedAxes.clear()
        publish()
    }

    private fun publish() {
        _coordinates.value = AxisCoordinates(
            units = Triple(
                stepsToUnits(stepCounts[0], 0),
                stepsToUnits(stepCounts[1], 1),
                stepsToUnits(stepCounts[2], 2)
            ),
            valid = Triple(validAxes[0], validAxes[1], validAxes[2])
        )
    }

    private companion object {
        // Matches the firmware's HOMING_TIMEOUT_MS (90 s) plus margin
        const val HOMING_TIMEOUT_MS = 120_000L
        const val MOVE_SETTLE_MS = 300L
        // The firmware's homing task clears its "homing active" flag (which rejects MOVE
        // commands) up to one 50 ms tick after notifying homed — wait it out
        const val HOMED_SETTLE_MS = 300L
        // One 200 ms device-position notification interval plus margin — waited out before
        // trusting the synced frame
        const val FW_POSITION_SYNC_MS = 500L

        // Reference quantum per unit type: sizes the calibration retreat and sanity checks
        const val COARSE_QUANTUM_MM = 5f
        const val COARSE_QUANTUM_DEG = 2f
        // Endstop back-off before the firmware's slow re-seek, in coarse quanta
        const val RETREAT_COARSE_QUANTA = 2
        // The whole hardware sequence (two full transits and the seeks) must fit in this
        const val CALIB_TOTAL_TIMEOUT_MS = 300_000L
    }
}

private fun <T> Triple<T, T, T>.at(index: Int): T = when (index) {
    0 -> first
    1 -> second
    else -> third
}

private fun <T> Triple<T, T, T>.with(index: Int, value: T): Triple<T, T, T> = when (index) {
    0 -> copy(first = value)
    1 -> copy(second = value)
    else -> copy(third = value)
}
