package net.agolyakov.agoslider.data.repository

import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice

class DeviceRepository() {
    private val _deviceList = listOf (
        AgoSliderDevice(
            deviceName = "LED Lamp",
            macAddress = "11:22:33:44:55:66"
        ),
        AgoSliderDevice(
            deviceName = "Matrix Clock YEY",
            macAddress = "11:22:33:44:55:77",
            friendlyName = "Часы Андрея в гостиной"
        ),
        AgoSliderDevice(
            deviceName = "Matrix Clock BRW",
            macAddress = "11:22:33:44:55:21",
            friendlyName = "Часы в десткой"
        ),
        AgoSliderDevice(
            deviceName = "Matrix Clock 1",
            macAddress = "11:22:33:44:55:41",
        ),
        AgoSliderDevice(
            deviceName = "Matrix Clock 2",
            macAddress = "11:22:33:44:55:da"
        ),
        AgoSliderDevice(
            deviceName = "Matrix Clock 3",
            macAddress = "11:22:33:44:55:bd",
        )
    )

    fun getDeviceList(): List<AgoSliderDevice> {
        return _deviceList
    }
}