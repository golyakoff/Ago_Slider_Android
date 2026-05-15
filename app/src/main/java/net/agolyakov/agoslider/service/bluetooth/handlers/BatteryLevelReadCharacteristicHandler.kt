package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class BatteryLevelReadCharacteristicHandler(
    private val batteryLevelFlow: MutableStateFlow<Int>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val level = bytes[0].toInt() and 0xFF
        batteryLevelFlow.value = level // 0-255, for percent use: level * 100 / 255
    }
}