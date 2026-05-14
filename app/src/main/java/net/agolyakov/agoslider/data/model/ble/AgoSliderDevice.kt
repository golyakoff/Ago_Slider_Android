package net.agolyakov.agoslider.data.model.ble

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AgoSliderDevice(
    val deviceName: String,             // Имя устройства, заданное производителем
    val macAddress: String,             // MAC-адрес
    val friendlyName: String? = null,   // Опционально заданное пользователем имя
) : Parcelable {
    fun getDisplayName(): String {
        return friendlyName ?: deviceName
    }
}
