package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PowerInfoReadCharacteristicHandler(
    private val powerInfoFlow: MutableStateFlow<Triple<Float, Float, Float>>,
    /** Called for every reading, so the session history keeps the values the flow conflates. */
    private val onSample: (Float, Float, Float) -> Unit = { _, _, _ -> }
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.size < 12) return

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val voltage = buffer.float
        val current = buffer.float
        val power = buffer.float

        powerInfoFlow.value = Triple(voltage, current, power)
        onSample(voltage, current, power)
    }
}