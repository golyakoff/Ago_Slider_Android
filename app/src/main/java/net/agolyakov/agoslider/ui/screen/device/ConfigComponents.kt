package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
import kotlin.math.roundToInt

// ----------------------------------------------------------------------------
// Reusable per-axis (X, C, B) configuration components.
//
// The cards are stateless: SettingsTabContent owns the edited copy of the
// device's values and decides when a card is dirty, so that a card can also read
// another card's unsaved edits — the speed card needs the microsteps and the
// units per step to show what a speed means in mm/s or deg/s.
// ----------------------------------------------------------------------------
private val AXES = listOf("X", "C", "B")

@Composable
fun ConfigCard(
    title: String,
    dirty: Boolean,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
            Button(onClick = onSave, enabled = dirty) {
                Text(stringResource(R.string.settings_save))
            }
        }
    }
}

/** Per-axis choice out of a fixed set of values, e.g. microsteps. */
@Composable
fun IntDropdownTriple(
    title: String,
    values: Triple<Int, Int, Int>,
    options: List<Int>,
    dirty: Boolean,
    onValueChange: (Triple<Int, Int, Int>) -> Unit,
    onSave: () -> Unit
) {
    ConfigCard(title = title, dirty = dirty, onSave = onSave) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisDropdown(AXES[0], values.first, options, Modifier.weight(1f)) {
                onValueChange(values.copy(first = it))
            }
            AxisDropdown(AXES[1], values.second, options, Modifier.weight(1f)) {
                onValueChange(values.copy(second = it))
            }
            AxisDropdown(AXES[2], values.third, options, Modifier.weight(1f)) {
                onValueChange(values.copy(third = it))
            }
        }
    }
}

/** Per-axis value on a slider snapped to [step], e.g. currents and acceleration. */
@Composable
fun IntSliderTriple(
    title: String,
    values: Triple<Int, Int, Int>,
    range: IntRange,
    step: Int,
    dirty: Boolean,
    onValueChange: (Triple<Int, Int, Int>) -> Unit,
    onSave: () -> Unit
) {
    ConfigCard(title = title, dirty = dirty, onSave = onSave) {
        AxisSlider(
            axis = AXES[0],
            value = values.first,
            range = range,
            step = step,
            onValueChange = { onValueChange(values.copy(first = it)) }
        )
        AxisSlider(
            axis = AXES[1],
            value = values.second,
            range = range,
            step = step,
            onValueChange = { onValueChange(values.copy(second = it)) }
        )
        AxisSlider(
            axis = AXES[2],
            value = values.third,
            range = range,
            step = step,
            onValueChange = { onValueChange(values.copy(third = it)) }
        )
    }
}

/**
 * Like [IntSliderTriple], but each axis also shows what its step rate works out to in the
 * axis's own unit, which is what the operator actually thinks in.
 */
@Composable
fun AxisSpeedTriple(
    title: String,
    values: Triple<Int, Int, Int>,
    range: IntRange,
    step: Int,
    microsteps: Triple<Int, Int, Int>,
    unitsPerStep: Triple<Float, Float, Float>,
    axisIsDegrees: Triple<Boolean, Boolean, Boolean>,
    dirty: Boolean,
    onValueChange: (Triple<Int, Int, Int>) -> Unit,
    onSave: () -> Unit
) {
    ConfigCard(title = title, dirty = dirty, onSave = onSave) {
        AxisSlider(
            axis = AXES[0],
            value = values.first,
            range = range,
            step = step,
            caption = unitsPerSecondLabel(values.first, microsteps.first, unitsPerStep.first, axisIsDegrees.first),
            onValueChange = { onValueChange(values.copy(first = it)) }
        )
        AxisSlider(
            axis = AXES[1],
            value = values.second,
            range = range,
            step = step,
            caption = unitsPerSecondLabel(values.second, microsteps.second, unitsPerStep.second, axisIsDegrees.second),
            onValueChange = { onValueChange(values.copy(second = it)) }
        )
        AxisSlider(
            axis = AXES[2],
            value = values.third,
            range = range,
            step = step,
            caption = unitsPerSecondLabel(values.third, microsteps.third, unitsPerStep.third, axisIsDegrees.third),
            onValueChange = { onValueChange(values.copy(third = it)) }
        )
    }
}

/** Per-axis unit: millimetres for a linear axis, degrees for a rotary one. */
@Composable
fun AxisUnitTriple(
    title: String,
    values: Triple<Boolean, Boolean, Boolean>,
    dirty: Boolean,
    onValueChange: (Triple<Boolean, Boolean, Boolean>) -> Unit,
    onSave: () -> Unit
) {
    ConfigCard(title = title, dirty = dirty, onSave = onSave) {
        AxisUnitRow(AXES[0], values.first) { onValueChange(values.copy(first = it)) }
        AxisUnitRow(AXES[1], values.second) { onValueChange(values.copy(second = it)) }
        AxisUnitRow(AXES[2], values.third) { onValueChange(values.copy(third = it)) }
    }
}

@Composable
fun BoolTriple(
    title: String,
    values: Triple<Boolean, Boolean, Boolean>,
    dirty: Boolean,
    onValueChange: (Triple<Boolean, Boolean, Boolean>) -> Unit,
    onSave: () -> Unit
) {
    ConfigCard(title = title, dirty = dirty, onSave = onSave) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${AXES[0]}: ")
                Switch(
                    checked = values.first,
                    onCheckedChange = { onValueChange(values.copy(first = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${AXES[1]}: ")
                Switch(
                    checked = values.second,
                    onCheckedChange = { onValueChange(values.copy(second = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${AXES[2]}: ")
                Switch(
                    checked = values.third,
                    onCheckedChange = { onValueChange(values.copy(third = it)) }
                )
            }
        }
    }
}

/**
 * Free-form per-axis decimals. Unlike the other cards this one keeps its editor state, because
 * half-typed input ("0.") is not a value the rest of the screen can use; [onValueChange] reports
 * null until all three fields parse again.
 */
@Composable
fun FloatTriple(
    title: String,
    deviceValues: Triple<Float, Float, Float>,
    dirty: Boolean,
    onValueChange: (Triple<Float, Float, Float>?) -> Unit,
    onSave: () -> Unit
) {
    var x by remember(deviceValues) { mutableStateOf(deviceValues.first.toString()) }
    var c by remember(deviceValues) { mutableStateOf(deviceValues.second.toString()) }
    var b by remember(deviceValues) { mutableStateOf(deviceValues.third.toString()) }

    fun report() {
        val parsed = listOf(x.toFloatOrNull(), c.toFloatOrNull(), b.toFloatOrNull())
        onValueChange(
            if (parsed.any { it == null }) null else Triple(parsed[0]!!, parsed[1]!!, parsed[2]!!)
        )
    }

    ConfigCard(title = title, dirty = dirty, onSave = onSave) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisTextField(AXES[0], x, { x = it; report() }, Modifier.weight(1f))
            AxisTextField(AXES[1], c, { c = it; report() }, Modifier.weight(1f))
            AxisTextField(AXES[2], b, { b = it; report() }, Modifier.weight(1f))
        }
    }
}

// ----------------------------------------------------------------------------
// Single-axis controls
// ----------------------------------------------------------------------------

/**
 * A step rate in the axis's own unit. The rate is in STEP pulses, i.e. microsteps, while
 * `units per step` is per full motor step — hence the division.
 */
@Composable
private fun unitsPerSecondLabel(
    stepsPerSecond: Int,
    microsteps: Int,
    unitsPerStep: Float,
    isDegrees: Boolean
): String {
    if (microsteps <= 0) return ""
    val unitsPerSecond = stepsPerSecond.toFloat() / microsteps * unitsPerStep
    val formatted = "%.1f".format(unitsPerSecond)
    return stringResource(
        if (isDegrees) R.string.settings_deg_per_sec else R.string.settings_mm_per_sec,
        formatted
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AxisDropdown(
    axis: String,
    value: Int,
    options: List<Int>,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(axis) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString()) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AxisSlider(
    axis: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    caption: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(axis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // Slider counts the positions between the ends, not the ends themselves
            steps = (range.last - range.first) / step - 1,
            modifier = Modifier.weight(1f)
        )
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(if (caption == null) 48.dp else 104.dp)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun AxisUnitRow(
    axis: String,
    isDegrees: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(axis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp))
        UnitOption(R.string.unit_mm, selected = !isDegrees) { onValueChange(false) }
        UnitOption(R.string.unit_deg, selected = isDegrees) { onValueChange(true) }
    }
}

@Composable
private fun UnitOption(
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AxisTextField(
    axis: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(axis) },
        singleLine = true,
        modifier = modifier
    )
}
