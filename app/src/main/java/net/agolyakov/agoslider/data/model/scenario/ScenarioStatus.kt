package net.agolyakov.agoslider.data.model.scenario

/**
 * Status of a scenario running on the device (SCENARIO, 0xF007).
 *
 * The layout is the same for every movement pattern, which is what lets the app follow — and
 * a future app version follow a pattern this one has never heard of. Crucially the run lives
 * on the slider, not here: the phone may disconnect, be closed or reboot mid-pass, and the
 * status read on reconnect still says exactly where the run got to.
 */
data class ScenarioStatus(
    val scenarioId: Int,
    val state: State,
    val reason: Reason,
    val elapsedMs: Long,
    val totalMs: Long
) {
    enum class State { IDLE, RUNNING, DONE, ABORTED, FAILED, UNKNOWN }

    enum class Reason { NONE, USER_STOP, LIMIT, MOTORS_OFF, NOT_HOMED, BUSY, BAD_PARAMS, UNKNOWN_ID, UNKNOWN }

    val progress: Float
        get() = if (totalMs > 0) (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    val remainingMs: Long
        get() = (totalMs - elapsedMs).coerceAtLeast(0L)

    companion object {
        const val ID_NONE = 0
        const val ID_FOCUS = 1

        fun stateOf(raw: Int) = when (raw) {
            0 -> State.IDLE
            1 -> State.RUNNING
            2 -> State.DONE
            3 -> State.ABORTED
            4 -> State.FAILED
            else -> State.UNKNOWN
        }

        fun reasonOf(raw: Int) = when (raw) {
            0 -> Reason.NONE
            1 -> Reason.USER_STOP
            2 -> Reason.LIMIT
            3 -> Reason.MOTORS_OFF
            4 -> Reason.NOT_HOMED
            5 -> Reason.BUSY
            6 -> Reason.BAD_PARAMS
            7 -> Reason.UNKNOWN_ID
            else -> Reason.UNKNOWN
        }
    }
}
