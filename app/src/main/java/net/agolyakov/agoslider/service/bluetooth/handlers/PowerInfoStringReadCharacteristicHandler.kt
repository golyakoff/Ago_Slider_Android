package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class PowerInfoStringReadCharacteristicHandler(
    private val powerInfoStringFlow: MutableStateFlow<String>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value
        if (bytes == null) {
            powerInfoStringFlow.value = ""
            return
        }
        // Convert bytes to UTF-8 string, trim null terminators
        val str = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
        powerInfoStringFlow.value = str
    }
}