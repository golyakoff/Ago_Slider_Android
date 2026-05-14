package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import net.agolyakov.agoslider.data.model.ble.AgoSliderTime
import no.nordicsemi.android.ble.data.Data

class TimeReadCharacteristicHandler (
    private var time: MutableStateFlow<AgoSliderTime>
): ReadCharacteristicHandler {
    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        time.value = AgoSliderTime.Companion.fromByteArray(data.value!!)
    }
}