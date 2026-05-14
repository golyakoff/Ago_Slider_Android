package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class RtcTemperatureReadCharacteristicHandler(
    private var temperatureInCelsius: MutableStateFlow<Float>
): ReadCharacteristicHandler {
    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {

        val upperByte = data.value!![0].toInt() and 0xFF
        var temperature = convertTwoComplement(upperByte)

        if (data.value!!.size == 2)
        {
            val lowerByte = data.value!![1].toInt() and 0xFF
            val fractionalPart = (lowerByte shr 6) and 0x03
            temperature += fractionalPart * 0.25f
        }

        temperatureInCelsius.value = temperature
    }

    private fun convertTwoComplement(byteValue: Int): Float {
        return if (byteValue and 0x80 != 0) {
            val inverted = (byteValue.inv() and 0xFF) + 1
            -inverted.toFloat()
        } else {
            byteValue.toFloat()
        }
    }
}