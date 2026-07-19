package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.power.PowerSample
import java.util.Locale
import kotlin.math.abs

// ----------------------------------------------------------------------------
// Session power chart.
//
// Voltage (~20 V), current (~0.05 A) and power (~1 W) differ by three orders of
// magnitude, so they are NOT drawn as three lines in one plot area: on a shared
// axis the two small series would collapse onto the baseline, and giving each its
// own hidden y-scale would make the line heights mutually meaningless. Instead
// each series gets its own panel over a shared time axis — small multiples — so
// every panel can carry an honest, labelled y-range.
// ----------------------------------------------------------------------------

/** Categorical slots 1-3 of the reference palette, stepped per mode. */
private val VOLTS_LIGHT = Color(0xFF2A78D6)
private val VOLTS_DARK = Color(0xFF3987E5)
private val AMPS_LIGHT = Color(0xFF008300)
private val AMPS_DARK = Color(0xFF008300)
private val WATTS_LIGHT = Color(0xFFE87BA4)
private val WATTS_DARK = Color(0xFFD55181)

private enum class PowerSeries { VOLTS, AMPERES, WATTS }

@Composable
fun PowerChart(history: List<PowerSample>, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(PowerSeries.entries.toSet()) }
    val dark = isSystemInDarkTheme()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A legend is also the filter: one row of checkboxes above the panels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (series in PowerSeries.entries) {
                SeriesCheckbox(
                    label = stringResource(series.labelRes()),
                    color = series.color(dark),
                    checked = series in visible,
                    modifier = Modifier.weight(1f),
                    onToggle = {
                        visible = if (series in visible) visible - series else visible + series
                    }
                )
            }
        }

        if (history.size < 2) {
            Text(
                stringResource(R.string.service_power_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            return@Column
        }

        val startMs = history.first().timestampMs
        val spanMs = (history.last().timestampMs - startMs).coerceAtLeast(1L)

        for (series in PowerSeries.entries) {
            if (series !in visible) continue
            SeriesPanel(
                values = history.map { series.valueOf(it) },
                times = history.map { (it.timestampMs - startMs).toFloat() / spanMs },
                color = series.color(dark),
                unit = stringResource(series.unitRes()),
                decimals = series.decimals()
            )
        }

        if (visible.isNotEmpty()) {
            val minutes = spanMs / 60000L
            Text(
                text = if (minutes >= 1L) {
                    stringResource(R.string.service_power_chart_span, minutes)
                } else {
                    stringResource(R.string.service_power_chart_span_sec, spanMs / 1000L)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * One measure: its own y-range printed at the ends, the latest value direct-labelled, and the
 * line itself. The x positions are shared with every other panel, so the panels read as one
 * chart split by measure.
 */
@Composable
private fun SeriesPanel(
    values: List<Float>,
    times: List<Float>,
    color: Color,
    unit: String,
    decimals: Int
) {
    val min = values.min()
    val max = values.max()
    // A supply that has not moved all session is the normal case, not an error: rather than
    // divide by zero and pin the line to the floor, such a series is centred and its
    // range ticks are dropped — repeating one number three times says nothing.
    val flat = abs(max - min) < 1e-6f
    val range = if (flat) 1f else max - min
    val grid = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (flat) "" else "${format(max, decimals)} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "${format(values.last(), decimals)} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 4.dp)
        ) {
            drawLine(
                color = grid,
                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
            // Inset the plotted band so a peak or a trough is not clipped by the panel edge
            val top = size.height * 0.12f
            val usable = size.height * 0.76f
            val path = Path()
            values.forEachIndexed { i, value ->
                val x = times[i] * size.width
                val y = if (flat) {
                    size.height / 2f
                } else {
                    top + usable - ((value - min) / range) * usable
                }
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        if (!flat) {
            Text(
                text = "${format(min, decimals)} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SeriesCheckbox(
    label: String,
    color: Color,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        // The box doubles as the series swatch, so the identity colour costs no extra width
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

private fun format(value: Float, decimals: Int) =
    String.format(Locale.US, "%.${decimals}f", value)

private fun PowerSeries.valueOf(sample: PowerSample) = when (this) {
    PowerSeries.VOLTS -> sample.volts
    PowerSeries.AMPERES -> sample.amperes
    PowerSeries.WATTS -> sample.watts
}

private fun PowerSeries.color(dark: Boolean) = when (this) {
    PowerSeries.VOLTS -> if (dark) VOLTS_DARK else VOLTS_LIGHT
    PowerSeries.AMPERES -> if (dark) AMPS_DARK else AMPS_LIGHT
    PowerSeries.WATTS -> if (dark) WATTS_DARK else WATTS_LIGHT
}

private fun PowerSeries.labelRes() = when (this) {
    PowerSeries.VOLTS -> R.string.service_power_voltage
    PowerSeries.AMPERES -> R.string.service_power_current
    PowerSeries.WATTS -> R.string.service_power_watts
}

private fun PowerSeries.unitRes() = when (this) {
    PowerSeries.VOLTS -> R.string.unit_volt
    PowerSeries.AMPERES -> R.string.unit_ampere
    PowerSeries.WATTS -> R.string.unit_watt
}

/** Current swings in the tens of milliamps, so it needs more digits than the other two. */
private fun PowerSeries.decimals() = if (this == PowerSeries.AMPERES) 3 else 2
