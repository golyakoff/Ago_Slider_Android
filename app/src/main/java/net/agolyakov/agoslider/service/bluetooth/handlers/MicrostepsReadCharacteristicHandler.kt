package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class MicrostepsReadCharacteristicHandler(
    private val microstepsFlow: MutableStateFlow<Triple<Int, Int, Int>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value
        if (bytes == null || bytes.size < 3) {
            return
        }

        // Convert each byte according to encoding: 0 -> 256, otherwise the value itself
        fun convert(byte: Byte): Int = if (byte == 0.toByte()) 256 else byte.toInt()

        val x = convert(bytes[0])
        val c = convert(bytes[1])
        val b = convert(bytes[2])

        microstepsFlow.value = Triple(x, c, b)
    }
}