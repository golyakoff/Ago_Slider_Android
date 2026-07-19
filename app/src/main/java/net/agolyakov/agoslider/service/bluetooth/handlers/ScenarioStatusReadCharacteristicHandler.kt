package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import net.agolyakov.agoslider.data.model.scenario.ScenarioStatus
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScenarioStatusReadCharacteristicHandler(
    private val statusFlow: MutableStateFlow<ScenarioStatus?>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.size < 11) return

        val buffer = ByteBuffer.wrap(bytes, 3, 8).order(ByteOrder.LITTLE_ENDIAN)
        statusFlow.value = ScenarioStatus(
            scenarioId = bytes[0].toInt() and 0xFF,
            state = ScenarioStatus.stateOf(bytes[1].toInt() and 0xFF),
            reason = ScenarioStatus.reasonOf(bytes[2].toInt() and 0xFF),
            elapsedMs = buffer.int.toLong() and 0xFFFFFFFFL,
            totalMs = buffer.int.toLong() and 0xFFFFFFFFL
        )
    }
}
