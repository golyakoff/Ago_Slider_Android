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
// Each card edits a local copy of the device's values and only offers SAVE once
// that copy differs from what the device reports: an enabled button means there
// is something unsent. The local copy is keyed on the device values, so it
// resets whenever the device reports something new.
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
    onValueChange: (Int, Int, Int) -> Unit
) {
    var x by remember(values) { mutableStateOf(values.first) }
    var c by remember(values) { mutableStateOf(values.second) }
    var b by remember(values) { mutableStateOf(values.third) }

    ConfigCard(
        title = title,
        dirty = Triple(x, c, b) != values,
        onSave = { onValueChange(x, c, b) }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisDropdown(AXES[0], x, options, { x = it }, Modifier.weight(1f))
            AxisDropdown(AXES[1], c, options, { c = it }, Modifier.weight(1f))
            AxisDropdown(AXES[2], b, options, { b = it }, Modifier.weight(1f))
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
    onValueChange: (Int, Int, Int) -> Unit
) {
    var x by remember(values) { mutableStateOf(values.first) }
    var c by remember(values) { mutableStateOf(values.second) }
    var b by remember(values) { mutableStateOf(values.third) }

    ConfigCard(
        title = title,
        dirty = Triple(x, c, b) != values,
        onSave = { onValueChange(x, c, b) }
    ) {
        AxisSlider(AXES[0], x, range, step) { x = it }
        AxisSlider(AXES[1], c, range, step) { c = it }
        AxisSlider(AXES[2], b, range, step) { b = it }
    }
}

/** Per-axis unit: millimetres for a linear axis, degrees for a rotary one. */
@Composable
fun AxisUnitTriple(
    title: String,
    values: Triple<Boolean, Boolean, Boolean>,
    onValueChange: (Boolean, Boolean, Boolean) -> Unit
) {
    var x by remember(values) { mutableStateOf(values.first) }
    var c by remember(values) { mutableStateOf(values.second) }
    var b by remember(values) { mutableStateOf(values.third) }

    ConfigCard(
        title = title,
        dirty = Triple(x, c, b) != values,
        onSave = { onValueChange(x, c, b) }
    ) {
        AxisUnitRow(AXES[0], x) { x = it }
        AxisUnitRow(AXES[1], c) { c = it }
        AxisUnitRow(AXES[2], b) { b = it }
    }
}

@Composable
fun ConfigTriple(
    title: String,
    values: Triple<Int, Int, Int>,
    onValueChange: (Int, Int, Int) -> Unit
) {
    var x by remember(values) { mutableStateOf(values.first.toString()) }
    var c by remember(values) { mutableStateOf(values.second.toString()) }
    var b by remember(values) { mutableStateOf(values.third.toString()) }

    val edited = Triple(x.toIntOrNull(), c.toIntOrNull(), b.toIntOrNull())

    ConfigCard(
        title = title,
        // Unparsable text is not something we can send, so it does not count as a change
        dirty = edited.toList().none { it == null } && edited != Triple(values.first, values.second, values.third),
        onSave = {
            onValueChange(
                x.toIntOrNull() ?: values.first,
                c.toIntOrNull() ?: values.second,
                b.toIntOrNull() ?: values.third
            )
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisTextField(AXES[0], x, { x = it }, Modifier.weight(1f))
            AxisTextField(AXES[1], c, { c = it }, Modifier.weight(1f))
            AxisTextField(AXES[2], b, { b = it }, Modifier.weight(1f))
        }
    }
}

@Composable
fun FloatTriple(
    title: String,
    values: Triple<Float, Float, Float>,
    onValueChange: (Float, Float, Float) -> Unit
) {
    var x by remember(values) { mutableStateOf(values.first.toString()) }
    var c by remember(values) { mutableStateOf(values.second.toString()) }
    var b by remember(values) { mutableStateOf(values.third.toString()) }

    val edited = Triple(x.toFloatOrNull(), c.toFloatOrNull(), b.toFloatOrNull())

    ConfigCard(
        title = title,
        dirty = edited.toList().none { it == null } && edited != Triple(values.first, values.second, values.third),
        onSave = {
            onValueChange(
                x.toFloatOrNull() ?: values.first,
                c.toFloatOrNull() ?: values.second,
                b.toFloatOrNull() ?: values.third
            )
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisTextField(AXES[0], x, { x = it }, Modifier.weight(1f))
            AxisTextField(AXES[1], c, { c = it }, Modifier.weight(1f))
            AxisTextField(AXES[2], b, { b = it }, Modifier.weight(1f))
        }
    }
}

@Composable
fun BoolTriple(
    title: String,
    values: Triple<Boolean, Boolean, Boolean>,
    onValueChange: (Boolean, Boolean, Boolean) -> Unit
) {
    var x by remember(values) { mutableStateOf(values.first) }
    var c by remember(values) { mutableStateOf(values.second) }
    var b by remember(values) { mutableStateOf(values.third) }

    ConfigCard(
        title = title,
        dirty = Triple(x, c, b) != values,
        onSave = { onValueChange(x, c, b) }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${AXES[0]}: ")
                Switch(checked = x, onCheckedChange = { x = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${AXES[1]}: ")
                Switch(checked = c, onCheckedChange = { c = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${AXES[2]}: ")
                Switch(checked = b, onCheckedChange = { b = it })
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Single-axis controls
// ----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AxisDropdown(
    axis: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
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
    onValueChange: (Int) -> Unit
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
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
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
