package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PositionReadCharacteristicHandler(
    // Null until the first value arrives — which doubles as "the firmware supports POSITION"
    private val positionFlow: MutableStateFlow<Triple<Int, Int, Int>?>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.size < 12) return

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        positionFlow.value = Triple(buffer.int, buffer.int, buffer.int)
    }
}
