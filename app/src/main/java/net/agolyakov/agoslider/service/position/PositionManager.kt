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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.agolyakov.agoslider.data.local.PositioningPreferences
import net.agolyakov.agoslider.data.model.ble.ConnectionState
import net.agolyakov.agoslider.data.model.position.AxisCoordinates
import net.agolyakov.agoslider.data.model.position.CalibrationPhase
import net.agolyakov.agoslider.data.model.position.CalibrationState
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

        // Strict validity policy: losing the link or the holding torque means the position can
        // no longer be proven — require a fresh homing
        scope.launch {
            bluetoothService.connectionState.collect { state ->
                val lost = state is ConnectionState.Disconnected ||
                        state is ConnectionState.Disconnecting ||
                        state is ConnectionState.Error
                if (lost) {
                    calibrationJob?.cancel()
                    invalidateAll()
                }
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

        // An unexpected limit switch hit: the firmware may have force-stopped the move, so the
        // commanded count has diverged from reality for that axis. Watches the monotonic hit
        // counters, not the conflatable state flow, so brief hits are not missed.
        scope.launch {
            var last = bluetoothService.limitHitCounts.value
            bluetoothService.limitHitCounts.collect { counts ->
                for (axis in 0..2) {
                    if (counts.at(axis) > last.at(axis) && axis !in protectedAxes) {
                        Log.w(tag, "Unexpected limit hit on axis $axis, position invalidated")
                        invalidateAxis(axis)
                    }
                }
                last = counts
            }
        }

        // Once an axis reports homed, drive it off the endstop by the home offset — that
        // position becomes coordinate 0
        scope.launch {
            bluetoothService.homeStatus.collect { status ->
                for (axis in 0..2) {
                    if (status.homed.at(axis) && pendingHomeOffset.remove(axis)) {
                        applyHomeOffset(axis)
                    }
                }
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
     * Measure the usable range of [axis]: home it, apply the home offset (coordinate 0), then
     * creep toward max in quanta until the endstop answers — the firmware does not stop moves
     * in the positive direction, so the approach must be quantized with a switch check after
     * every quantum. min is derived from the home offset, max from the fine-pass reading.
     */
    fun startCalibration(axis: Int) {
        if (calibrationJob?.isActive == true) return
        calibrationJob = scope.launch {
            // The flow temporarily drops the firmware's virtual-limit flag (so that leaving
            // an active switch is not force-stopped) and lowers the axis speed for the
            // precise stages — restore the user's settings whatever happens
            val originalVirtualLimit = bluetoothService.virtualLimit.value.at(axis)
            val originalSpeed = bluetoothService.axisSpeed.value
            try {
                runCalibration(axis, originalSpeed.at(axis))
            } catch (e: CancellationException) {
                _calibration.value = CalibrationState(axis, CalibrationPhase.IDLE)
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Calibration failed", e)
                _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            } finally {
                protectedAxes.remove(axis)
                setVirtualLimitForAxis(axis, originalVirtualLimit)
                if (originalSpeed.at(axis) > 0) {
                    setAxisSpeedForAxis(axis, originalSpeed.at(axis))
                }
            }
        }
    }

    fun cancelCalibration() {
        if (calibrationJob?.isActive != true) return
        val axis = _calibration.value.axis
        calibrationJob?.cancel()
        // A commanded move keeps running on the device after the coroutine dies — stop it
        // the same way the sweep brake does (HOME with an empty mask = forceStop), and drop
        // the axis: the force stop desyncs whatever count it had
        bluetoothService.sendHomeCommand(false, false, false)
        axis?.let { invalidateAxis(it) }
    }

    private suspend fun runCalibration(axis: Int, fastSpeedSetting: Int) {
        val degrees = bluetoothService.axisUnit.value.at(axis)
        val coarse = if (degrees) COARSE_QUANTUM_DEG else COARSE_QUANTUM_MM
        val fine = if (degrees) FINE_QUANTUM_DEG else FINE_QUANTUM_MM
        val slowSpeedSetting = (fastSpeedSetting / SLOW_SPEED_DIVISOR).coerceAtLeast(SLOW_SPEED_MIN)

        Log.i(
            tag,
            "Calibration axis=$axis start: speed=${bluetoothService.axisSpeed.value} " +
                    "accel=${bluetoothService.axisAccel.value} " +
                    "microsteps=${bluetoothService.microsteps.value} " +
                    "unitsPerStep=${bluetoothService.unitsPerStep.value} " +
                    "coarse=$coarse fine=$fine settings=${_settings.value}"
        )
        val ready = bluetoothService.connectionState.value is ConnectionState.Ready &&
                bluetoothService.motorsEnabled.value &&
                unitsToSteps(coarse, axis) > 0 // steps-to-units geometry must be known
        if (!ready) {
            Log.w(tag, "Calibration axis=$axis not ready to start")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        // The flow assumes it starts away from both endstops: homing on an active switch
        // "completes" instantly at whichever end the carriage happens to press
        if (limitActive(axis)) {
            Log.w(tag, "Calibration axis=$axis: endstop active at start — move off the switch first")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        protectedAxes.add(axis)
        invalidateAxis(axis)

        // 1. Rough zero: a single fast homing. It only anchors the frame for the sweep —
        // the precise zero comes from the slow min-switch measurement on the way back.
        if (!homeAxisAndZero(axis, coarse, 0, fastSpeedSetting)) {
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        // 2. One continuous sweep to the far end. The virtual-limit flag goes OFF for the
        // whole far-end sequence: the firmware force-stops any negative motion on an active
        // switch, which would silently corrupt the step count — with the flag off, the
        // reversal below is a normal deceleration and the commanded target stays exact.
        // 2. Full-speed sweep to the far end; the virtual-limit flag goes OFF for the rest
        // of the run (its monitor force-stops negative motion on an active switch, which
        // would corrupt the count during the slow measurements below). The stored max, when
        // present, only BOUNDS the sweep as grind protection — it never drives the speed.
        setVirtualLimitForAxis(axis, false)
        delay(VL_TOGGLE_SETTLE_MS)
        _calibration.value = CalibrationState(axis, CalibrationPhase.MEASURING)
        // No distance assumptions at all — obtaining the span IS the point of calibrating,
        // so the sweep just drives until the switch answers. The only guards are the
        // timeout below and the Cancel button, both ending in a force-stop.
        // Overruns and estimate errors scale with how far the axis travels per BLE latency,
        // so all margins are speed-proportional with the quanta-based floor
        val fastUnitsPerSec = unitsPerSecond(axis)
        val stopBackoffUnits = maxOf(2 * FINE_BACKOFF_COARSE * coarse, 0.3f * fastUnitsPerSec)
        val returnMarginUnits = maxOf(ESCAPE_COARSE_QUANTA * coarse, 0.5f * fastUnitsPerSec)
        val sweepStartUnits = stepsToUnits(stepCounts[axis], axis)
        // The frame will not survive the coming force stop — don't show a running-away
        // coordinate in the meantime
        invalidateAxis(axis)
        val sweepStartMs = System.currentTimeMillis()
        val hitsBefore = bluetoothService.limitHitCounts.value.at(axis)
        sendAxisMove(axis, SWEEP_STEPS_UNBOUNDED)
        val hit = withTimeoutOrNull(SWEEP_TIMEOUT_MS) {
            bluetoothService.limitHitCounts.first { it.at(axis) > hitsBefore }
        }
        if (hit == null) {
            Log.w(tag, "Calibration axis=$axis: no endstop hit within the sweep timeout")
            bluetoothService.sendHomeCommand(false, false, false) // stop the unbounded move
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }
        val elapsedMs = System.currentTimeMillis() - sweepStartMs
        val estimatedMax = sweepStartUnits + stepsToUnits(profileDistanceSteps(elapsedMs, axis), axis)
        // Instant stop, the same forceStop the firmware's homing uses: HOME with an empty
        // axis mask runs stop_all_axes(). Overrun past the switch is BLE latency travel
        // only — no deceleration distance. The force stop desyncs the commanded count, so
        // from here until the min-switch re-anchor the frame is relative-only.
        bluetoothService.sendHomeCommand(false, false, false)
        Log.i(tag, "Calibration axis=$axis: endstop after ${elapsedMs}ms, estimated max=$estimatedMax, force-stopped")
        delay(STOP_SETTLE_MS)
        invalidateAxis(axis)

        // 3. Back off the switch and measure it precisely with the slow approach
        _calibration.value = CalibrationState(axis, CalibrationPhase.BACKOFF)
        jog(axis, -stopBackoffUnits)
        var extraRetreats = 0
        while (limitActive(axis) && extraRetreats < 3) {
            jog(axis, -FINE_BACKOFF_COARSE * coarse)
            extraRetreats++
        }
        if (limitActive(axis)) {
            Log.w(tag, "Calibration axis=$axis: could not back off the far endstop")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }
        setAxisSpeedForAxis(axis, slowSpeedSetting)
        delay(SPEED_SETTLE_MS)
        if (!locateSwitch(axis, direction = 1, coarse, fine, seekBound = stopBackoffUnits + 6 * coarse)) {
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }
        val maxTriggerSteps = stepCounts[axis]

        // 4. Fast return to just short of the min switch. The margin comes from THIS run's
        // own sweep estimate (±BLE latency travel), not from anything stored.
        setAxisSpeedForAxis(axis, fastSpeedSetting)
        delay(SPEED_SETTLE_MS)
        _calibration.value = CalibrationState(axis, CalibrationPhase.MEASURING)
        val offsetUnits = _settings.value.homeOffset.at(axis)
        val returnUnits = estimatedMax + offsetUnits - returnMarginUnits
        val returnSteps = unitsToSteps(returnUnits, axis)
        sendAxisMove(axis, -returnSteps)
        stepCounts[axis] -= returnSteps
        publish()
        delay(moveDurationMs(returnSteps, axis))
        if (limitActive(axis)) {
            Log.w(tag, "Calibration axis=$axis: min endstop already active after the return")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        // 5. Measure the min switch precisely the same way — this anchors the zero
        setAxisSpeedForAxis(axis, slowSpeedSetting)
        delay(SPEED_SETTLE_MS)
        if (!locateSwitch(axis, direction = -1, coarse, fine, seekBound = returnMarginUnits + 6 * coarse)) {
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }
        val minTriggerSteps = stepCounts[axis]

        // 6. Both switches are now slow-measured marks in the same relative frame:
        // max = trigger-to-trigger distance minus the offset that defines 0
        val measuredMax = stepsToUnits(maxTriggerSteps - minTriggerSteps, axis) - offsetUnits
        if (abs(measuredMax - estimatedMax) > maxOf(5 * coarse, 0.4f * fastUnitsPerSec)) {
            Log.w(tag, "Calibration axis=$axis: measured max=$measuredMax implausible vs estimate=$estimatedMax")
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        // 7. Rise from the min trigger by the offset — that point IS coordinate 0
        setAxisSpeedForAxis(axis, fastSpeedSetting)
        delay(SPEED_SETTLE_MS)
        _calibration.value = CalibrationState(axis, CalibrationPhase.OFFSET)
        val offsetSteps = unitsToSteps(offsetUnits, axis)
        if (offsetSteps != 0) {
            sendAxisMove(axis, offsetSteps)
            delay(moveDurationMs(offsetSteps, axis))
        }
        stepCounts[axis] = 0
        validAxes[axis] = true
        publish()
        if (offsetSteps > 0 && limitActive(axis)) {
            Log.w(tag, "Calibration axis=$axis: min endstop still active at parked 0")
            invalidateAxis(axis)
            _calibration.value = CalibrationState(axis, CalibrationPhase.FAILED)
            return
        }

        val settings = _settings.value
        saveSettings(
            settings.copy(
                limitMin = settings.limitMin.with(axis, -offsetUnits),
                limitMax = settings.limitMax.with(axis, measuredMax)
            )
        )
        _calibration.value = CalibrationState(axis, CalibrationPhase.DONE)
        Log.i(tag, "Axis $axis calibrated: min=${-offsetUnits} max=$measuredMax, parked at 0")
    }

    /**
     * Precisely locate the switch lying in [direction] (+1 toward max, -1 toward min):
     * slow continuous seek until the hit counter fires, smooth counter-stop (the count
     * survives — firmware move() is relative to the current target), back off a little,
     * then a fine quantized creep. On success stepCounts[axis] is the commanded count at
     * the trigger, accurate to the fine quantum. Expects: slow speed set, switch inactive,
     * virtual limit off.
     */
    private suspend fun locateSwitch(
        axis: Int,
        direction: Int,
        coarse: Float,
        fine: Float,
        seekBound: Float
    ): Boolean {
        _calibration.value = CalibrationState(axis, CalibrationPhase.FINE)
        val seekStartSteps = stepCounts[axis]
        val seekStartMs = System.currentTimeMillis()
        val seekSteps = unitsToSteps(seekBound, axis) * direction
        val hitsBefore = bluetoothService.limitHitCounts.value.at(axis)
        sendAxisMove(axis, seekSteps)
        stepCounts[axis] += seekSteps
        publish()
        val hit = withTimeoutOrNull(moveDurationMs(seekSteps, axis) + 2_000) {
            bluetoothService.limitHitCounts.first { it.at(axis) > hitsBefore }
        }
        if (hit == null) {
            Log.w(tag, "locateSwitch axis=$axis dir=$direction: no hit within $seekBound")
            return false
        }
        // Smooth counter-stop just short of the estimated trigger; at the slow speed the
        // overrun into the switch zone is a couple of units at most
        val elapsed = System.currentTimeMillis() - seekStartMs
        val estTriggerSteps = seekStartSteps + profileDistanceSteps(elapsed, axis) * direction
        val parkSteps = estTriggerSteps - unitsToSteps(FINE_BACKOFF_COARSE * coarse, axis) * direction
        val counterSteps = parkSteps - stepCounts[axis]
        sendAxisMove(axis, counterSteps)
        stepCounts[axis] += counterSteps
        publish()
        delay(moveDurationMs(unitsToSteps(2 * FINE_BACKOFF_COARSE * coarse, axis), axis) + 1_000)
        var retreats = 0
        while (limitActive(axis) && retreats < 3) {
            jog(axis, -direction * FINE_BACKOFF_COARSE * coarse)
            retreats++
        }
        if (limitActive(axis)) {
            Log.w(tag, "locateSwitch axis=$axis dir=$direction: switch would not release")
            return false
        }
        // Fine quantized creep to the trigger
        val fineHitsBefore = bluetoothService.limitHitCounts.value.at(axis)
        var travel = 0f
        while (!limitActive(axis) &&
            bluetoothService.limitHitCounts.value.at(axis) == fineHitsBefore
        ) {
            if (travel > 5 * coarse) {
                Log.w(tag, "locateSwitch axis=$axis dir=$direction: fine creep never hit")
                return false
            }
            jog(axis, direction * fine)
            travel += fine
        }
        return true
    }

    /**
     * Establish coordinate 0 for [axis]: fast homing seek, then — when the slow/fast speed
     * settings are provided — a small retreat and a slow re-seek for a precise anchor, and
     * finally the home-offset move that defines 0. Returns false on timeout.
     */
    private suspend fun homeAxisAndZero(
        axis: Int,
        coarse: Float,
        slowSpeedSetting: Int,
        fastSpeedSetting: Int
    ): Boolean {
        if (!homeAxisOnce(axis)) return false

        if (slowSpeedSetting in 1 until fastSpeedSetting) {
            // Retreat a little and re-seek slowly: homing stops on a 50 ms switch poll, so
            // the anchor's repeatability is proportional to the approach speed
            _calibration.value = CalibrationState(axis, CalibrationPhase.CLEARING)
            jog(axis, 2 * coarse) // positive = away from the min switch, never force-stopped
            setAxisSpeedForAxis(axis, slowSpeedSetting)
            delay(SPEED_SETTLE_MS)
            val ok = homeAxisOnce(axis)
            setAxisSpeedForAxis(axis, fastSpeedSetting)
            delay(SPEED_SETTLE_MS)
            if (!ok) return false
        }

        _calibration.value = CalibrationState(axis, CalibrationPhase.OFFSET)
        val offsetSteps = unitsToSteps(_settings.value.homeOffset.at(axis), axis)
        if (offsetSteps != 0) {
            sendAxisMove(axis, offsetSteps)
            delay(moveDurationMs(offsetSteps, axis))
        }
        stepCounts[axis] = 0
        validAxes[axis] = true
        publish()
        return true
    }

    /** One homing seek of [axis]; waits out the firmware's post-homing settle. */
    private suspend fun homeAxisOnce(axis: Int): Boolean {
        _calibration.value = CalibrationState(axis, CalibrationPhase.HOMING)
        bluetoothService.sendHomeCommand(axis == 0, axis == 1, axis == 2)
        // The status flow may still hold homed=true from an earlier homing; the firmware
        // notifies homed=false the moment homing starts — wait for that fresh start first,
        // otherwise the offset would be applied while the axis is still travelling
        if (withTimeoutOrNull(HOMING_START_TIMEOUT_MS) {
                bluetoothService.homeStatus.first { !it.homed.at(axis) }
            } == null
        ) {
            Log.w(tag, "Homing axis=$axis: no fresh start notification")
            return false
        }
        if (withTimeoutOrNull(HOMING_TIMEOUT_MS) {
                bluetoothService.homeStatus.first { it.homed.at(axis) }
            } == null
        ) {
            Log.w(tag, "Homing axis=$axis: timed out")
            return false
        }
        Log.i(tag, "Homing axis=$axis: homed")
        // The firmware rejects MOVE while its homing flag is still set and clears the flag
        // shortly after sending the homed notification — a silently dropped follow-up move
        // would shift the whole coordinate system
        delay(HOMED_SETTLE_MS)
        return true
    }

    private fun setAxisSpeedForAxis(axis: Int, value: Int) {
        val current = bluetoothService.axisSpeed.value
        bluetoothService.setAxisSpeed(
            if (axis == 0) value else current.first,
            if (axis == 1) value else current.second,
            if (axis == 2) value else current.third
        )
    }

    private fun setVirtualLimitForAxis(axis: Int, enabled: Boolean) {
        val current = bluetoothService.virtualLimit.value
        bluetoothService.setVirtualLimit(
            if (axis == 0) enabled else current.first,
            if (axis == 1) enabled else current.second,
            if (axis == 2) enabled else current.third
        )
    }

    /** Steps covered [elapsedMs] into a move, following the firmware's speed profile. */
    private fun profileDistanceSteps(elapsedMs: Long, axis: Int): Int {
        val speed = realSpeedStepsPerSec(axis)
        val accel = bluetoothService.axisAccel.value.at(axis).coerceAtLeast(1).toFloat()
        val t = elapsedMs / 1000f
        val rampTime = speed / accel
        val steps = if (t <= rampTime) {
            0.5f * accel * t * t
        } else {
            speed * t - 0.5f * speed * speed / accel
        }
        return steps.roundToInt()
    }

    /** One quantized calibration move: command it, count it, wait out its estimated duration. */
    private suspend fun jog(axis: Int, units: Float) {
        val steps = unitsToSteps(units, axis)
        val waitMs = moveDurationMs(steps, axis)
        Log.d(tag, "jog axis=$axis units=$units steps=$steps wait=${waitMs}ms limit=${limitActive(axis)}")
        sendAxisMove(axis, steps)
        stepCounts[axis] += steps
        publish()
        delay(waitMs)
    }

    private fun limitActive(axis: Int): Boolean = bluetoothService.limitStatus.value.at(axis)

    // ========================== Home offset ==========================

    private fun applyHomeOffset(axis: Int) {
        scope.launch {
            // Same firmware homing-flag race as in runCalibration — see the comment there
            delay(HOMED_SETTLE_MS)
            val offsetSteps = unitsToSteps(_settings.value.homeOffset.at(axis), axis)
            Log.d(tag, "Applying home offset on axis $axis: $offsetSteps steps")
            if (offsetSteps != 0) {
                sendAxisMove(axis, offsetSteps)
                delay(moveDurationMs(offsetSteps, axis))
            }
            stepCounts[axis] = 0
            validAxes[axis] = true
            protectedAxes.remove(axis)
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

    /**
     * The firmware programs the step period as 10^7/speed µs (app_main.cpp, setSpeedInUs),
     * so an axis actually runs at a tenth of its configured steps/s.
     */
    private fun realSpeedStepsPerSec(axis: Int): Float =
        bluetoothService.axisSpeed.value.at(axis).coerceAtLeast(10) / 10f

    /** Actual axis speed in its own unit (mm/s or deg/s) at the current settings. */
    private fun unitsPerSecond(axis: Int): Float {
        val microsteps = bluetoothService.microsteps.value.at(axis)
        if (microsteps == 0) return 0f
        return realSpeedStepsPerSec(axis) / microsteps *
                bluetoothService.unitsPerStep.value.at(axis)
    }

    /** The device moves in STEP pulses (microsteps) while `units per step` is per full step. */
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
        const val HOMING_START_TIMEOUT_MS = 5_000L
        const val MOVE_SETTLE_MS = 300L
        // The firmware's homing task clears its "homing active" flag (which rejects MOVE
        // commands) up to one 50 ms tick after notifying homed — wait it out
        const val HOMED_SETTLE_MS = 300L
        // Round trip for a virtual-limit flag or axis-speed write before the move that
        // depends on it
        const val VL_TOGGLE_SETTLE_MS = 400L
        const val SPEED_SETTLE_MS = 600L
        // Slow stage for the precise seeks: a fifth of the configured speed, floored
        const val SLOW_SPEED_DIVISOR = 5
        const val SLOW_SPEED_MIN = 1_000
        // After the empty-mask HOME force-stop: covers the firmware's brief homing-active
        // window (rejects MOVE) plus the stop itself
        const val STOP_SETTLE_MS = 700L

        // Calibration quanta: the far endstop does not stop the motor (the firmware only stops
        // moves toward it in the negative direction), so max is found by creeping — accuracy is
        // the fine quantum, overshoot past the switch is at most one coarse quantum
        const val COARSE_QUANTUM_MM = 5f
        const val FINE_QUANTUM_MM = 0.5f
        const val COARSE_QUANTUM_DEG = 2f
        const val FINE_QUANTUM_DEG = 0.2f
        // Return margin and the small backoff before a fine creep, in coarse quanta
        const val ESCAPE_COARSE_QUANTA = 6
        const val FINE_BACKOFF_COARSE = 2
        // The sweep drives "forever" — it ends on the switch hit, the timeout force-stop,
        // or the user's Cancel (also a force-stop)
        const val SWEEP_STEPS_UNBOUNDED = 100_000_000
        const val SWEEP_TIMEOUT_MS = 180_000L
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
