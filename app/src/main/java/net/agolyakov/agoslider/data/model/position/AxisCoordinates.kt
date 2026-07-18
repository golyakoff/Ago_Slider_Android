package net.agolyakov.agoslider.data.model.position

/**
 * Virtual coordinates of the three axes in their own units (mm or degrees), counted on the
 * phone from the relative moves it commands. An axis is [valid] only from a successful homing
 * until anything that could make the count diverge from reality: disconnect, motors off, an
 * unexpected limit switch hit, or a change of the steps-to-units geometry.
 */
data class AxisCoordinates(
    val units: Triple<Float, Float, Float>,
    val valid: Triple<Boolean, Boolean, Boolean>
) {
    companion object {
        val UNKNOWN = AxisCoordinates(Triple(0f, 0f, 0f), Triple(false, false, false))
    }
}
