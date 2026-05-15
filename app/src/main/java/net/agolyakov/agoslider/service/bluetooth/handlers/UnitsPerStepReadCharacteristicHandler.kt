package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UnitsPerStepReadCharacteristicHandler(
    private val unitsPerStepFlow: MutableStateFlow<Triple<Float, Float, Float>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.size < 12) return

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val x = buffer.float
        val c = buffer.float
        val b = buffer.float

        unitsPerStepFlow.value = Triple(x, c, b)
    }
}