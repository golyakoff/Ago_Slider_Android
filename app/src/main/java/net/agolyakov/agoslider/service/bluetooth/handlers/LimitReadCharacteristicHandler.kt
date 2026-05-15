package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class LimitReadCharacteristicHandler(
    private val limitFlow: MutableStateFlow<Triple<Boolean, Boolean, Boolean>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val flags = bytes[0].toInt()
        val xLimit = (flags and 0x01) != 0
        val cLimit = (flags and 0x02) != 0
        val bLimit = (flags and 0x04) != 0

        limitFlow.value = Triple(xLimit, cLimit, bLimit)
    }
}