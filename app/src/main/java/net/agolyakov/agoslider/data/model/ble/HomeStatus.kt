package net.agolyakov.agoslider.data.model.ble

data class HomeStatus(
    val requested: Triple<Boolean, Boolean, Boolean>,
    val homed: Triple<Boolean, Boolean, Boolean>
)