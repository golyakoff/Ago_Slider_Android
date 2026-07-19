package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import net.agolyakov.agoslider.data.model.position.DevicePosition
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PositionReadCharacteristicHandler(
    // Null until the first value arrives — which doubles as "the firmware supports POSITION"
    private val positionFlow: MutableStateFlow<DevicePosition?>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.size < 13) return

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val steps = Triple(buffer.int, buffer.int, buffer.int)
        val mask = bytes[12].toInt()
        positionFlow.value = DevicePosition(
            steps = steps,
            homeValid = Triple(mask and 0x01 != 0, mask and 0x02 != 0, mask and 0x04 != 0)
        )
    }
}
