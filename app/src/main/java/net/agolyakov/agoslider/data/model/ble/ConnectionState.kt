package net.agolyakov.agoslider.data.model.ble

sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnecting : ConnectionState()
    object Disconnected : ConnectionState()
    object Ready : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}