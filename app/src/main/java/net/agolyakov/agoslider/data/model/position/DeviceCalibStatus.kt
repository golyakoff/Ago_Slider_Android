package net.agolyakov.agoslider.data.model.position

/**
 * Status notification of the firmware's hardware calibration (CALIBRATE characteristic):
 * [phase] is the firmware's motion_calib_phase_t, [spanSteps] the measured
 * endstop-to-endstop distance in STEP pulses (valid from the PARK phase on).
 */
data class DeviceCalibStatus(
    val axis: Int,
    val phase: Int,
    val spanSteps: Int
) {
    companion object {
        const val PHASE_SEEK_MIN_FAST = 1
        const val PHASE_RETREAT_MIN = 2
        const val PHASE_SEEK_MIN_SLOW = 3
        const val PHASE_SEEK_MAX_FAST = 4
        const val PHASE_RETREAT_MAX = 5
        const val PHASE_SEEK_MAX_SLOW = 6
        const val PHASE_PARK = 7
        const val PHASE_DONE = 8
        const val PHASE_FAILED = 9
    }
}
