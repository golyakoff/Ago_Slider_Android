package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarm
import no.nordicsemi.android.ble.data.Data

class TurnOffAlarmReadCharacteristicHandler (
    private var turnOffAlarm: MutableStateFlow<AgoSliderAlarm>
): ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        turnOffAlarm.value = AgoSliderAlarm.Companion.fromByteArray(data.value!!)
    }
}