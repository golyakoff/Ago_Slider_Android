package net.agolyakov.agoslider.data.model.position

/**
 * The firmware's own view of where the axes are (POSITION, 0xF005).
 *
 * [homeValid] is what makes the coordinate survive a reconnect: the device keeps it true from
 * the moment an axis is zeroed at its endstop until the motors are disabled or the board
 * reboots, so the app never has to guess whether a reported zero is a homed one.
 */
data class DevicePosition(
    val steps: Triple<Int, Int, Int>,
    val homeValid: Triple<Boolean, Boolean, Boolean>
)
