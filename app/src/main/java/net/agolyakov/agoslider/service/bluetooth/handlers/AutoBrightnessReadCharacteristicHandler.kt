package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class AutoBrightnessReadCharacteristicHandler (
    private var isAutoBrightness: MutableStateFlow<Boolean>
): ReadCharacteristicHandler {
    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        isAutoBrightness.value = data.value!![0] > 0
    }
}