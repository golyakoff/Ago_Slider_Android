package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class AxisUnitReadCharacteristicHandler(
    private val axisUnitFlow: MutableStateFlow<Triple<Boolean, Boolean, Boolean>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val flags = bytes[0].toInt()
        val xDeg = (flags and 0x01) != 0
        val cDeg = (flags and 0x02) != 0
        val bDeg = (flags and 0x04) != 0

        axisUnitFlow.value = Triple(xDeg, cDeg, bDeg)
    }
}