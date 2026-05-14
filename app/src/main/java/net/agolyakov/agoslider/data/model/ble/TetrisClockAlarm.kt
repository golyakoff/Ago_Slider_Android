package net.agolyakov.agoslider.data.model.ble

data class AgoSliderAlarm(
    val isActive: Boolean = false,
    val hours: Byte = 0,
    val minutes: Byte = 0
) {
    companion object {
        const val TOTAL_MINUTES_MASK = 0x07FF // 0b0000011111111111
        const val IS_ACTIVE_MASK = 0x0800     // 0b0000100000000000

        fun fromByteArray(binaryData: ByteArray): AgoSliderAlarm {
            val byte0 = binaryData[0].toInt() and 0xFF
            val byte1 = binaryData[1].toInt() and 0xFF

            // Little-endian порядок: младший байт first
            val result = (byte1 shl 8) or byte0

            val isActive = (result and IS_ACTIVE_MASK) != 0
            val totalMinutes = result and TOTAL_MINUTES_MASK
            val hours = (totalMinutes / 60).toByte()
            val minutes = (totalMinutes % 60).toByte()

            return AgoSliderAlarm(isActive, hours, minutes)
        }
    }

    fun toByteArray(): ByteArray {
        val totalMinutes = minutes + hours * 60
        val result = if (isActive) totalMinutes or IS_ACTIVE_MASK else totalMinutes

        return byteArrayOf(
            (result and 0xff).toByte(),
            ((result shr 8) and 0xff).toByte()
        )
    }
}
