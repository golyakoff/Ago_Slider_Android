package net.agolyakov.agoslider.data.extensions

import android.bluetooth.le.ScanResult
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice

fun ScanResult.toBleDevice(): AgoSliderDevice {
    return AgoSliderDevice(
        deviceName = this.scanRecord?.deviceName ?: "<unnamed>",
        macAddress = this.device.address
    )
}
