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
    data class AimPoint(val xUnits: Float, val cUnits: Float, val bUnits: Float)

    data class Solution(
        val subjectAlongRail: Float,   // mm on the X coordinate scale
        val distance: Float,           // mm, perpendicular from the rail
        val cSign: Int,                // which way C turns as X advances
        val residualDeg: Float         // how well the three readings agree; a sanity check
    )

    data class State(
        val points: List<AimPoint?> = listOf(null, null, null),
        val activeIndex: Int = 0,
        val solution: Solution? = null,
        val error: Error? = null
    ) {
        enum class Error { NEED_THREE_POINTS, INCONSISTENT, NOT_READY, UNSUPPORTED, REFUSED }

        val definedCount: Int get() = points.count { it != null }
        val allDefined: Boolean get() = definedCount == 3

        /** A point may be visited once its predecessor exists — the travel is measured from it. */
        fun canActivate(index: Int) = index in 0..2 && index <= definedCount
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val status: StateFlow<ScenarioStatus?> = bluetoothService.scenarioStatus

    // ========================== Aiming ==========================

    fun jogX(deltaMm: Float) = positionManager.moveRelative(deltaMm, 0f, 0f)
    fun jogC(deltaDeg: Float) = positionManager.moveRelative(0f, deltaDeg, 0f)
    fun jogB(deltaDeg: Float) = positionManager.moveRelative(0f, 0f, deltaDeg)

    /** Where the axes stand, so the card can show them beside the jog buttons. */
    val coordinates = positionManager.coordinates

    /**
     * Make a point current and drive the carriage to it. An already-defined point sends X back
     * to its recorded position; the next undefined one sends X to where that mark belongs,
     * spreading the three over exactly the travel that will be filmed — which is also the
     * best-conditioned data the solver can get, since it measures the curve over the interval
     * that matters.
     */
    fun activatePoint(index: Int, xTravel: Float) {
        val state = _state.value
        if (!state.canActivate(index)) return
        _state.value = state.copy(activeIndex = index, error = null)

        val recorded = state.points[index]
        val here = positionManager.coordinates.value.units
        if (recorded != null) {
            // Returning to a point means returning the whole rig to it, not just the carriage:
            // the aim it holds is the combination of all three axes, and restoring X alone
            // would show the mark's position with the camera pointing somewhere else
            positionManager.moveRelative(
                recorded.xUnits - here.first,
                recorded.cUnits - here.second,
                recorded.bUnits - here.third
            )
            return
        }

        // An undefined point has no aim to restore yet — only the carriage knows where to go
        val first = state.points[0] ?: return             // point 1 is wherever the user put it
        val targetX = if (index == 1) first.xUnits + xTravel / 2f else first.xUnits + xTravel
        positionManager.moveAxisTo(0, targetX)
    }

    /** Fix the current point at the axes' present coordinates. */
    fun definePoint(xTravel: Float) {
        val coords = positionManager.coordinates.value
        if (!coords.valid.first || !coords.valid.second || !coords.valid.third) {
            _state.value = _state.value.copy(error = State.Error.NOT_READY)
            return
        }
        val state = _state.value
        val points = state.points.toMutableList()
        points[state.activeIndex] =
            AimPoint(coords.units.first, coords.units.second, coords.units.third)

        val filled = points.filterNotNull()
        val solution = if (filled.size == 3) solve(filled) else null
        val next = points.indexOfFirst { it == null }.takeIf { it >= 0 } ?: state.activeIndex
        _state.value = State(points = points, activeIndex = next, solution = solution)

        // Step the carriage on to where the next mark belongs
        if (next != state.activeIndex) activatePoint(next, xTravel)
    }

    /**
     * Send every axis back to the first point. The device tracks C relative to where it stands
     * when a run begins, so starting from the closing angle would have the camera already aimed
     * at the end of the shot; B likewise has to begin at its first recorded tilt.
     */
    fun goToStart() {
        val first = _state.value.points[0] ?: return
        val here = positionManager.coordinates.value.units
        positionManager.moveRelative(
            first.xUnits - here.first,
            first.cUnits - here.second,
            first.bUnits - here.third
        )
    }

    fun reset() {
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
        val a = aims.sortedBy { it.xUnits }
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
    /**
     * Hand the pass to the device.
     *
     * Both the distance and the tilt come from the three aimed points rather than from typed
     * numbers: the geometry was measured, and B simply runs from the tilt recorded at the first
     * point to the one recorded at the third.
     */
    fun start(seconds: Float): Boolean {
        if (!bluetoothService.scenarioSupported()) {
            _state.value = _state.value.copy(error = State.Error.UNSUPPORTED)
            return false
        }
        val state = _state.value
        val solution = state.solution
        val first = state.points[0]
        val last = state.points[2]
        if (solution == null || first == null || last == null) {
            _state.value = state.copy(error = State.Error.NEED_THREE_POINTS)
            return false
        }
        val coords = positionManager.coordinates.value
        if (!coords.valid.first || !coords.valid.second) {
            _state.value = state.copy(error = State.Error.NOT_READY)
            return false
        }

        // The device works in X pulses for the geometry: only the ratio of the two distances
        // matters there, so expressing both the same way avoids a unit conversion on the far side
        val subjectSteps = positionManager.unitsToStepsPublic(solution.subjectAlongRail, 0)
        val distanceSteps = positionManager.unitsToStepsPublic(solution.distance, 0)
        val xTravelSteps = positionManager.unitsToStepsPublic(last.xUnits - first.xUnits, 0)
        val bTravelSteps = positionManager.unitsToStepsPublic(last.bUnits - first.bUnits, 2)

        val payload = ByteBuffer.allocate(FOCUS_PAYLOAD_LEN).order(ByteOrder.LITTLE_ENDIAN)
        payload.putInt(subjectSteps)
        payload.putInt(distanceSteps)
        payload.putInt(xTravelSteps)
        payload.putInt(bTravelSteps)
        payload.put(solution.cSign.toByte())
        payload.put(0)

        val durationMs = (seconds * 1000f).toLong().coerceAtLeast(1L)
        Log.i(tag, "Start: subject=$subjectSteps d=$distanceSteps x=$xTravelSteps " +
            "b=$bTravelSteps sign=${solution.cSign} ${durationMs}ms")
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
