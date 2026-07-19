package net.agolyakov.agoslider.data.model.power

/** One INA219 reading, timestamped so the samples can be plotted against real time. */
data class PowerSample(
    val timestampMs: Long,
    val volts: Float,
    val amperes: Float,
    val watts: Float
)
