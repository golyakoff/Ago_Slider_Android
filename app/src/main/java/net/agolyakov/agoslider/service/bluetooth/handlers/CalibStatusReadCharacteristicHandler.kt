package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import net.agolyakov.agoslider.data.model.position.DeviceCalibStatus
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CalibStatusReadCharacteristicHandler(
    private val statusFlow: MutableStateFlow<DeviceCalibStatus?>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.size < 6) return

        val span = ByteBuffer.wrap(bytes, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val status = DeviceCalibStatus(
            axis = bytes[0].toInt(),
            phase = bytes[1].toInt(),
            spanSteps = span
        )
        statusFlow.value = status
    }
}
