package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.ble.data.Data

interface ReadCharacteristicHandler {
    fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) { }
}
