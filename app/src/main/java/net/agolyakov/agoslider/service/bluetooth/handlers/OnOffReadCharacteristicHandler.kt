package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class OnOffReadCharacteristicHandler (
    private var isOn: MutableStateFlow<Boolean>
): ReadCharacteristicHandler {
    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        isOn.value = data.value!![0] > 0
    }
}