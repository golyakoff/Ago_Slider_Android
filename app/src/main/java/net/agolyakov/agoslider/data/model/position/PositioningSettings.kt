package net.agolyakov.agoslider.data.model.position

/**
 * Per-device virtual coordinate settings, in each axis's own unit (mm or degrees).
 * Stored on the phone, not on the device — see PositioningPreferences.
 *
 * The axis layout, with 0 at the homed position:
 *
 *   [min]----------[0]------------------------[max]
 *       |home offset|
 *
 * [homeOffset] is how far the axis moves away from the endstop after homing to reach
 * coordinate 0: positive = against the homing direction, negative = along it.
 */
data class PositioningSettings(
    val homeOffset: Triple<Float, Float, Float>,
    val limitMin: Triple<Float, Float, Float>,
    val limitMax: Triple<Float, Float, Float>
) {
    companion object {
        val DEFAULT = PositioningSettings(
            homeOffset = Triple(5f, 0f, 0f),
            limitMin = Triple(0f, -5f, -90f),
            limitMax = Triple(900f, 5f, 90f)
        )
    }
}
