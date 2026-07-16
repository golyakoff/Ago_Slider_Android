package net.agolyakov.agoslider.data.model.ble

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AgoSliderDevice(
    val deviceName: String,             // Device name assigned by the manufacturer
    val macAddress: String,             // MAC address
    val friendlyName: String? = null,   // Optional user-assigned name
) : Parcelable {
    fun getDisplayName(): String {
        return friendlyName ?: deviceName
    }
}
