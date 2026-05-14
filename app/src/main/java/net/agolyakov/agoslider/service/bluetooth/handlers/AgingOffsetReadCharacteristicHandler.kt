package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class AgingOffsetReadCharacteristicHandler (
    private var agingOffset: MutableStateFlow<Int>
): ReadCharacteristicHandler {
    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val unsigned = data.value!![0].toInt() and 0xFF

        agingOffset.value = if (unsigned and 0x80 != 0) {
            unsigned - 256
        } else {
            unsigned
        }
    }
}