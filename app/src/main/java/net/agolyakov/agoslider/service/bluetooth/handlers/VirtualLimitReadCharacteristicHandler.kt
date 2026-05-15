package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class VirtualLimitReadCharacteristicHandler(
    private val virtualLimitFlow: MutableStateFlow<Triple<Boolean, Boolean, Boolean>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val flags = bytes[0].toInt()
        val xEn = (flags and 0x01) != 0
        val cEn = (flags and 0x02) != 0
        val bEn = (flags and 0x04) != 0

        virtualLimitFlow.value = Triple(xEn, cEn, bEn)
    }
}