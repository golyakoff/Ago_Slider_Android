package net.agolyakov.agoslider.data.model.position

enum class CalibrationPhase {
    IDLE,

    /** Homing the axis to its endstop. */
    HOMING,

    /** Moving from the endstop by the home offset — this becomes coordinate 0. */
    OFFSET,

    /** Moving away from the endstop until its signal clears. */
    CLEARING,

    /** Coarse pass toward max until the endstop triggers. */
    MEASURING,

    /** Backing off the endstop before the fine pass. */
    BACKOFF,

    /** Fine pass toward max for the final reading. */
    FINE,

    DONE,
    FAILED;

    val running: Boolean
        get() = this != IDLE && this != DONE && this != FAILED
}

/** State of the limit calibration flow; [axis] is 0=X, 1=C, 2=B (null when never started). */
data class CalibrationState(
    val axis: Int? = null,
    val phase: CalibrationPhase = CalibrationPhase.IDLE
)
