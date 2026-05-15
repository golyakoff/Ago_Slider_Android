package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class InvertDirReadCharacteristicHandler(
    private val invertDirFlow: MutableStateFlow<Triple<Boolean, Boolean, Boolean>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val flags = bytes[0].toInt()
        val xInv = (flags and 0x01) != 0
        val cInv = (flags and 0x02) != 0
        val bInv = (flags and 0x04) != 0

        invertDirFlow.value = Triple(xInv, cInv, bInv)
    }
}