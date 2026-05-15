package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class MotEnReadCharacteristicHandler(
    private val motorsEnabledFlow: MutableStateFlow<Boolean>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        motorsEnabledFlow.value = bytes[0] != 0.toByte()
    }
}