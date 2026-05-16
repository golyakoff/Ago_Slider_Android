package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import net.agolyakov.agoslider.data.model.ble.HomeStatus
import no.nordicsemi.android.ble.data.Data

class HomeReadCharacteristicHandler(
    private val homeFlow: MutableStateFlow<HomeStatus>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val status = bytes[0].toInt()
        val requested = Triple(
            (status and 0x10) != 0,
            (status and 0x20) != 0,
            (status and 0x40) != 0
        )
        val homed = Triple(
            (status and 0x01) != 0,
            (status and 0x02) != 0,
            (status and 0x04) != 0
        )
        homeFlow.value = HomeStatus(requested, homed)
    }
}