package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

/**
 * Which axes turn full circles past a single index magnet instead of running between two
 * endstops. Calibrating such an axis measures a revolution less the magnet's trigger zone,
 * and its zero is the magnet itself rather than a point half-way along the travel.
 */
class ContinuousReadCharacteristicHandler(
    private val continuousFlow: MutableStateFlow<Triple<Boolean, Boolean, Boolean>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val flags = bytes[0].toInt()
        continuousFlow.value = Triple(
            (flags and 0x01) != 0,
            (flags and 0x02) != 0,
            (flags and 0x04) != 0
        )
    }
}
