package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class VersionReadCharacteristicHandler (
    private var version: MutableStateFlow<String>,
    private val readEvent: MutableStateFlow<Unit?>
): ReadCharacteristicHandler {
    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val newVersion = data.value!!.toString(Charsets.US_ASCII)
        version.value = newVersion
        readEvent.value = Unit
    }
}