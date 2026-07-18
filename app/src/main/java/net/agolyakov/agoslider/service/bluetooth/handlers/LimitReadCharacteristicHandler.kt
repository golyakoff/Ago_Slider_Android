package net.agolyakov.agoslider.service.bluetooth.handlers

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.ble.data.Data

class LimitReadCharacteristicHandler(
    private val limitFlow: MutableStateFlow<Triple<Boolean, Boolean, Boolean>>,
    // Rising-edge counters per axis. StateFlow conflates fast value changes, so a switch that
    // triggers and releases between two collector resumptions would be invisible in
    // [limitFlow]; the counters are monotonic, making every hit observable no matter how
    // briefly the switch was active.
    private val hitCountFlow: MutableStateFlow<Triple<Int, Int, Int>>
) : ReadCharacteristicHandler {

    override fun onReadCharacteristicCallback(device: BluetoothDevice, data: Data) {
        val bytes = data.value ?: return
        if (bytes.isEmpty()) return

        val flags = bytes[0].toInt()
        val xLimit = (flags and 0x01) != 0
        val cLimit = (flags and 0x02) != 0
        val bLimit = (flags and 0x04) != 0

        val previous = limitFlow.value
        limitFlow.value = Triple(xLimit, cLimit, bLimit)

        val counts = hitCountFlow.value
        hitCountFlow.value = Triple(
            counts.first + if (xLimit && !previous.first) 1 else 0,
            counts.second + if (cLimit && !previous.second) 1 else 0,
            counts.third + if (bLimit && !previous.third) 1 else 0
        )
    }
}
