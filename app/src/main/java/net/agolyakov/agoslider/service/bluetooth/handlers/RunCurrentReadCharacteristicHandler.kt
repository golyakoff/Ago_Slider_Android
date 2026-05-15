package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RunCurrentReadCharacteristicHandler(
    private val runCurrentFlow: MutableStateFlow<Triple<Int, Int, Int>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.size < 6) return

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val x = buffer.short.toInt() and 0xFFFF
        val c = buffer.short.toInt() and 0xFFFF
        val b = buffer.short.toInt() and 0xFFFF

        runCurrentFlow.value = Triple(x, c, b)
    }
}