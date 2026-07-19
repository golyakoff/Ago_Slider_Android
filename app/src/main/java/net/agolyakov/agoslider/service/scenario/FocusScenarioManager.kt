package net.agolyakov.agoslider.service.scenario

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.agolyakov.agoslider.data.model.scenario.ScenarioStatus
import net.agolyakov.agoslider.service.bluetooth.BluetoothService
import net.agolyakov.agoslider.service.position.PositionManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sign

/**
 * Sets up the "focus on subject" pass and hands it to the device.
 *
 * The app only prepares and starts: once written, the run belongs to the slider and keeps going
 * whether or not the phone is connected, awake or even installed. Everything here is therefore
 * about working out the geometry, never about steering the axes in flight.
 */
@Singleton
class FocusScenarioManager @Inject constructor(
    private val positionManager: PositionManager,
    private val bluetoothService: BluetoothService
) {
    private val tag = "FocusScenario"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Aiming needs three points, not two. With two, the app knows only the DIFFERENCE between
     * the C angles — the angle that points square at the rail is never known — and that
     * difference is exactly symmetric about the midpoint of the two aim points: a subject 20 cm
     * to the left of centre and one 20 cm to the right produce identical readings while needing
     * mirrored tracking. A third reading breaks the symmetry, and as a bonus makes the geometry
     * fully determined: both the subject's position along the rail AND its distance fall out of
     * the measurements, so the distance no longer has to be judged by eye.
     */
    data class AimPoint(val xUnits: Float, val cUnits: Float)

    data class Solution(
        val subjectAlongRail: Float,   // mm on the X coordinate scale
        val distance: Float,           // mm, perpendicular from the rail
        val cSign: Int,                // which way C turns as X advances
        val residualDeg: Float         // how well the three readings agree; a sanity check
    )

    data class State(
        val aims: List<AimPoint> = emptyList(),
        val solution: Solution? = null,
        val error: Error? = null
    ) {
        enum class Error { NEED_THREE_POINTS, INCONSISTENT, NOT_READY, UNSUPPORTED, REFUSED }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val status: StateFlow<ScenarioStatus?> = bluetoothService.scenarioStatus

    // ========================== Aiming ==========================

    /** Nudge C while the user lines the subject up in the frame. */
    fun jogC(deltaDeg: Float) = positionManager.moveRelative(0f, deltaDeg, 0f)

    /** Nudge X while the user places the carriage. */
    fun jogX(deltaMm: Float) = positionManager.moveRelative(deltaMm, 0f, 0f)

    fun moveXTo(target: Float) = positionManager.moveAxisTo(0, target)

    /** Where the carriage stands, so the card can show it next to the jog buttons. */
    val coordinates = positionManager.coordinates

    /**
     * Record where C ended up for the X position it was aimed from, then drive X to where the
     * next aim belongs. The three points are spread over exactly the travel the pass will use
     * — start, middle, end — which is both the least the user has to think about and the best
     * conditioned data for the solver, since it measures the curve over the interval that will
     * actually be filmed.
     */
    fun markAim(xTravel: Float) {
        val coords = positionManager.coordinates.value
        if (!coords.valid.first || !coords.valid.second) {
            _state.value = _state.value.copy(error = State.Error.NOT_READY)
            return
        }
        val aims = _state.value.aims + AimPoint(coords.units.first, coords.units.second)
        _state.value = State(aims = aims, solution = if (aims.size >= 3) solve(aims) else null)

        // The first aim fixes where the pass begins; the rest follow from the travel
        val start = aims.first()
        when (aims.size) {
            1 -> positionManager.moveAxisTo(0, start.xUnits + xTravel / 2f)
            2 -> positionManager.moveAxisTo(0, start.xUnits + xTravel)
            3 -> {
                // Both axes must go back, not just the carriage. The device tracks C relative
                // to wherever it stands when the run begins, so leaving C at the angle it held
                // at the END of the pass would mean the camera starts already aimed where it
                // should finish — and then barely turns at all. Sent as one command so the two
                // axes travel together.
                val here = positionManager.coordinates.value.units
                positionManager.moveRelative(
                    start.xUnits - here.first,
                    start.cUnits - here.second,
                    0f
                )
            }
        }
    }

    fun clearAims() {
        _state.value = State()
    }

    /**
     * Recovers the subject's position and distance from three aimed angles.
     *
     * Each reading is c(x) = offset + sign * atan((subject - x) / distance). Differencing
     * removes the unknown offset and leaves two equations in two unknowns. They are smooth but
     * not analytically invertible, so this is a coarse grid search refined by repeatedly
     * shrinking the window around the best cell — a few thousand evaluations, once, which is
     * nothing on a phone and is far more robust than a Newton solve that can walk off a cliff.
     */
    private fun solve(aims: List<AimPoint>): Solution? {
        val a = aims.takeLast(3).sortedBy { it.xUnits }
        val (p1, p2, p3) = Triple(a[0], a[1], a[2])
        val span = p3.xUnits - p1.xUnits
        if (span <= 0f) return null

        // Both brackets have the same sign by construction, so the tracking direction can be
        // read straight off the measurements instead of being guessed or configured
        val d12 = p1.cUnits - p2.cUnits
        val d23 = p2.cUnits - p3.cUnits
        if (d12 == 0f && d23 == 0f) return null
        if (d12 != 0f && d23 != 0f && sign(d12) != sign(d23)) {
            // The subject cannot have been passed in two directions during one sweep
            return null
        }
        val cSign = if ((d12 + d23) >= 0f) 1 else -1
        val t12 = abs(d12).toDouble() * DEG_TO_RAD
        val t23 = abs(d23).toDouble() * DEG_TO_RAD

        fun residual(subject: Double, distance: Double): Double {
            val f1 = atan((subject - p1.xUnits) / distance) - atan((subject - p2.xUnits) / distance)
            val f2 = atan((subject - p2.xUnits) / distance) - atan((subject - p3.xUnits) / distance)
            val e1 = abs(f1) - t12
            val e2 = abs(f2) - t23
            return e1 * e1 + e2 * e2
        }

        var loSubject = (p1.xUnits - 4f * span).toDouble()
        var hiSubject = (p3.xUnits + 4f * span).toDouble()
        var loDistance = 50.0            // 5 cm: closer than any real setup
        var hiDistance = 60_000.0        // 60 m: effectively infinity for this rig
        var best = Triple(0.0, 0.0, Double.MAX_VALUE)

        repeat(REFINEMENTS) {
            val subjectStep = (hiSubject - loSubject) / GRID
            val distanceStep = (hiDistance - loDistance) / GRID
            var bestSubject = loSubject
            var bestDistance = loDistance
            var bestErr = Double.MAX_VALUE
            for (i in 0..GRID) {
                val subject = loSubject + subjectStep * i
                for (j in 0..GRID) {
                    val distance = loDistance + distanceStep * j
                    if (distance <= 0.0) continue
                    val err = residual(subject, distance)
                    if (err < bestErr) {
                        bestErr = err
                        bestSubject = subject
                        bestDistance = distance
                    }
                }
            }
            best = Triple(bestSubject, bestDistance, bestErr)
            // Shrink around the best cell but keep a generous margin. The residual surface has
            // a long flat valley, and clamping straight down to the winning cell strands the
            // search inside it — with a wide margin the same grid converges on the exact
            // answer, without it the recovered geometry can be hundreds of millimetres out.
            loSubject = bestSubject - MARGIN * subjectStep
            hiSubject = bestSubject + MARGIN * subjectStep
            loDistance = (bestDistance - MARGIN * distanceStep).coerceAtLeast(10.0)
            hiDistance = bestDistance + MARGIN * distanceStep
        }

        val residualDeg = (Math.sqrt(best.third) / DEG_TO_RAD).toFloat()
        Log.i(tag, "Solved: subject=${best.first} distance=${best.second} " +
            "sign=$cSign residual=$residualDeg deg")
        return Solution(
            subjectAlongRail = best.first.toFloat(),
            distance = best.second.toFloat(),
            cSign = cSign,
            residualDeg = residualDeg
        )
    }

    // ========================== Running ==========================

    /**
     * @param xTravel  signed travel in mm
     * @param bTravel  signed travel in degrees
     * @param seconds  how long the whole pass should take
     * @param distanceOverrideMm  set to use a typed-in distance instead of the solved one;
     *                            null keeps the measured value, 0 means infinity (C stays put)
     */
    fun start(xTravel: Float, bTravel: Float, seconds: Float, distanceOverrideMm: Float?): Boolean {
        if (!bluetoothService.scenarioSupported()) {
            _state.value = _state.value.copy(error = State.Error.UNSUPPORTED)
            return false
        }
        val solution = _state.value.solution
        val distanceMm = distanceOverrideMm ?: solution?.distance
        if (distanceMm == null && distanceOverrideMm == null) {
            _state.value = _state.value.copy(error = State.Error.NEED_THREE_POINTS)
            return false
        }
        val coords = positionManager.coordinates.value
        if (!coords.valid.first) {
            _state.value = _state.value.copy(error = State.Error.NOT_READY)
            return false
        }

        // The device works in X pulses for the geometry: only the ratio of the two distances
        // matters there, so expressing both the same way avoids a unit conversion on the far side
        val subjectSteps = positionManager.unitsToStepsPublic(
            solution?.subjectAlongRail ?: coords.units.first, 0
        )
        val distanceSteps = if (distanceMm == null || distanceMm <= 0f) 0
        else positionManager.unitsToStepsPublic(distanceMm, 0)
        val xTravelSteps = positionManager.unitsToStepsPublic(xTravel, 0)
        val bTravelSteps = positionManager.unitsToStepsPublic(bTravel, 2)
        val cSign = solution?.cSign ?: 1

        val payload = ByteBuffer.allocate(FOCUS_PAYLOAD_LEN).order(ByteOrder.LITTLE_ENDIAN)
        payload.putInt(subjectSteps)
        payload.putInt(distanceSteps)
        payload.putInt(xTravelSteps)
        payload.putInt(bTravelSteps)
        payload.put(cSign.toByte())
        payload.put(0)

        val durationMs = (seconds * 1000f).toLong().coerceAtLeast(1L)
        Log.i(tag, "Start: subject=$subjectSteps d=$distanceSteps x=$xTravelSteps " +
            "b=$bTravelSteps sign=$cSign ${durationMs}ms")
        val sent = bluetoothService.sendScenarioStart(
            ScenarioStatus.ID_FOCUS, durationMs, payload.array()
        )
        if (!sent) _state.value = _state.value.copy(error = State.Error.REFUSED)
        return sent
    }

    fun stop() {
        bluetoothService.sendScenarioStop()
    }

    companion object {
        private const val DEG_TO_RAD = Math.PI / 180.0
        private const val GRID = 80
        private const val REFINEMENTS = 25
        private const val MARGIN = 6
        private const val FOCUS_PAYLOAD_LEN = 18
    }
}
