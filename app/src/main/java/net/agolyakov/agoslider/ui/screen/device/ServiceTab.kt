package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme
import kotlin.math.roundToInt

// ----------------------------------------------------------------------------
// Service tab: low-level per-axis relative moves for checking that the motors
// work, plus raw hardware status (limit switches, power monitor)
// ----------------------------------------------------------------------------
@Composable
fun ServiceTabContent(
    moveX: Int,
    moveC: Int,
    moveB: Int,
    axisUnit: Triple<Boolean, Boolean, Boolean>,
    limitStatus: Triple<Boolean, Boolean, Boolean>,
    powerInfo: Triple<Float, Float, Float>,
    powerInfoString: String,
    firmwareVersion: String,
    onMoveXChange: (Int) -> Unit,
    onMoveCChange: (Int) -> Unit,
    onMoveBChange: (Int) -> Unit,
    onSendMoveCommand: () -> Unit,
    onCheckFirmwareUpdates: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Move command, in each axis's own unit — the sliders rest at 0, so a move is dialled
        // in as a distance either side of "stay put" instead of typed as a signed number
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.service_move_title),
                    style = MaterialTheme.typography.titleMedium
                )
                MoveAxisSlider("X", moveX, MOVE_X_RANGE, MOVE_STEP, axisUnit.first, onMoveXChange)
                MoveAxisSlider("C", moveC, MOVE_C_RANGE, MOVE_STEP, axisUnit.second, onMoveCChange)
                MoveAxisSlider("B", moveB, MOVE_B_RANGE, MOVE_STEP, axisUnit.third, onMoveBChange)
                Button(onClick = onSendMoveCommand) {
                    Text(stringResource(R.string.service_move_button))
                }
            }
        }

        HorizontalDivider()

        // Read-only hardware status
        Text(
            stringResource(
                R.string.service_limit_switches,
                limitStatus.first.toString(),
                limitStatus.second.toString(),
                limitStatus.third.toString()
            )
        )
        Text(
            stringResource(
                R.string.service_power,
                powerInfo.first.toString(),
                powerInfo.second.toString(),
                powerInfo.third.toString()
            )
        )
        Text(stringResource(R.string.service_power_string, powerInfoString))

        HorizontalDivider()

        // Firmware update
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    stringResource(R.string.service_firmware_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.service_firmware_version, firmwareVersion))
                Button(onClick = onCheckFirmwareUpdates) {
                    Text(stringResource(R.string.service_check_updates))
                }
            }
        }
    }
}

private val MOVE_X_RANGE = -500..500
private val MOVE_C_RANGE = -360..360
private val MOVE_B_RANGE = -300..300
private const val MOVE_STEP = 10

@Composable
private fun MoveAxisSlider(
    axis: String,
    value: Int,
    range: IntRange,
    step: Int,
    isDegrees: Boolean,
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
            text = stringResource(
                if (isDegrees) R.string.service_move_deg else R.string.service_move_mm,
                value
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
    }
}

// ----------------------------------------------------------------------------
// Previews
// ----------------------------------------------------------------------------
@Composable
private fun ServiceTabPreview(darkTheme: Boolean) {
    AgoSliderTheme(darkTheme) {
        ServiceTabContent(
            moveX = 0,
            moveC = 30,
            moveB = -20,
            axisUnit = Triple(false, true, true),
            limitStatus = Triple(false, true, false),
            powerInfo = Triple(21.48f, 0.082f, 1.76f),
            powerInfoString = "21.48V 0.082A 1.76W",
            firmwareVersion = "v0.1.0",
            onMoveXChange = {},
            onMoveCChange = {},
            onMoveBChange = {},
            onSendMoveCommand = {},
            onCheckFirmwareUpdates = {}
        )
    }
}

@Preview(name = "Light Theme", showBackground = true)
@Composable
private fun ServiceTabLightPreview() {
    ServiceTabPreview(darkTheme = false)
}

@Preview(name = "Dark Theme", showBackground = true)
@Composable
private fun ServiceTabDarkPreview() {
    ServiceTabPreview(darkTheme = true)
}
