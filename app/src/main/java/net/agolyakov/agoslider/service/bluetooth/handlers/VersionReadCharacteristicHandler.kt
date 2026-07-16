package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class VersionReadCharacteristicHandler (
    private var version: MutableStateFlow<String>,
    private val readEvent: MutableStateFlow<Unit?>
): ReadCharacteristicHandler {
    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        // The device sends its whole fixed-size buffer, so stop at the string terminator —
        // otherwise the trailing NUL padding renders as a wide blank run.
        val newVersion = data.value!!.toString(Charsets.US_ASCII)
            .takeWhile { it.code != 0 }
            .trim()
        version.value = newVersion
        readEvent.value = Unit
    }
}
