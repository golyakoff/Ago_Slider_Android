package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.position.CalibrationPhase
import net.agolyakov.agoslider.data.model.position.CalibrationState
import net.agolyakov.agoslider.data.model.position.PositioningSettings
import net.agolyakov.agoslider.data.model.power.PowerSample
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
    powerHistory: List<PowerSample>,
    firmwareVersion: String,
    calibration: CalibrationState,
    positioning: PositioningSettings,
    motorsEnabled: Boolean,
    onMoveXChange: (Int) -> Unit,
    onMoveCChange: (Int) -> Unit,
    onMoveBChange: (Int) -> Unit,
    onSendMoveCommand: () -> Unit,
    onCalibrate: (Int) -> Unit,
    onCancelCalibration: () -> Unit,
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
                CardTitle(Icons.Default.OpenWith, stringResource(R.string.service_move_title))
                MoveAxisSlider("X", moveX, MOVE_X_RANGE, MOVE_STEP, axisUnit.first, onMoveXChange)
                MoveAxisSlider("C", moveC, MOVE_C_RANGE, MOVE_STEP, axisUnit.second, onMoveCChange)
                MoveAxisSlider("B", moveB, MOVE_B_RANGE, MOVE_STEP, axisUnit.third, onMoveBChange)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSendMoveCommand) {
                        Text(stringResource(R.string.service_move_button))
                    }
                    // Outlined, so returning the sliders to "stay put" never competes for
                    // attention with the button that actually moves the hardware
                    OutlinedButton(
                        onClick = {
                            onMoveXChange(0)
                            onMoveCChange(0)
                            onMoveBChange(0)
                        },
                        enabled = moveX != 0 || moveC != 0 || moveB != 0
                    ) {
                        Text(stringResource(R.string.service_move_reset))
                    }
                }
            }
        }

        CalibrationCard(
            calibration = calibration,
            positioning = positioning,
            axisUnit = axisUnit,
            motorsEnabled = motorsEnabled,
            onCalibrate = onCalibrate,
            onCancelCalibration = onCancelCalibration
        )

        // Read-only hardware status, one card per piece of hardware
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardTitle(Icons.Default.Sensors, stringResource(R.string.service_limits_title))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AxisStateDot("X", limitStatus.first)
                    AxisStateDot("C", limitStatus.second)
                    AxisStateDot("B", limitStatus.third)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardTitle(Icons.Default.BatteryFull, stringResource(R.string.service_battery_title))
                Text(powerInfoString)
                PowerChart(history = powerHistory)
            }
        }

        // Firmware update
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                CardTitle(Icons.Default.SystemUpdate, stringResource(R.string.service_firmware_title))
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

// ----------------------------------------------------------------------------
// Limit calibration: per-axis "find the travel range" flow driven by
// PositionManager; shows the stored range when idle and the phase while running
// ----------------------------------------------------------------------------
@Composable
private fun CalibrationCard(
    calibration: CalibrationState,
    positioning: PositioningSettings,
    axisUnit: Triple<Boolean, Boolean, Boolean>,
    motorsEnabled: Boolean,
    onCalibrate: (Int) -> Unit,
    onCancelCalibration: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardTitle(Icons.Default.SquareFoot, stringResource(R.string.service_calibration_title))
            Text(
                stringResource(R.string.service_calibration_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            val axes = listOf("X", "C", "B")
            val mins = listOf(positioning.limitMin.first, positioning.limitMin.second, positioning.limitMin.third)
            val maxs = listOf(positioning.limitMax.first, positioning.limitMax.second, positioning.limitMax.third)
            val degrees = listOf(axisUnit.first, axisUnit.second, axisUnit.third)
            for (axis in 0..2) {
                val running = calibration.axis == axis && calibration.phase.running
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        axes[axis],
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = if (calibration.axis == axis && calibration.phase != CalibrationPhase.IDLE) {
                            calibrationPhaseLabel(calibration.phase)
                        } else {
                            stringResource(
                                if (degrees[axis]) R.string.calib_range_deg else R.string.calib_range_mm,
                                mins[axis], maxs[axis]
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (running) {
                        Button(onClick = onCancelCalibration) {
                            Text(stringResource(R.string.service_calibration_cancel))
                        }
                    } else {
                        Button(
                            onClick = { onCalibrate(axis) },
                            enabled = motorsEnabled && !calibration.phase.running
                        ) {
                            Text(stringResource(R.string.service_calibrate_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun calibrationPhaseLabel(phase: CalibrationPhase): String = stringResource(
    when (phase) {
        CalibrationPhase.HOMING -> R.string.calib_phase_homing
        CalibrationPhase.OFFSET -> R.string.calib_phase_offset
        CalibrationPhase.CLEARING -> R.string.calib_phase_clearing
        CalibrationPhase.MEASURING -> R.string.calib_phase_measuring
        CalibrationPhase.BACKOFF -> R.string.calib_phase_backoff
        CalibrationPhase.FINE -> R.string.calib_phase_fine
        CalibrationPhase.DONE, CalibrationPhase.IDLE -> R.string.calib_phase_done
        CalibrationPhase.FAILED -> R.string.calib_phase_failed
    }
)

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
            powerHistory = emptyList(),
            firmwareVersion = "v0.1.0",
            calibration = CalibrationState(),
            positioning = PositioningSettings.DEFAULT,
            motorsEnabled = true,
            onMoveXChange = {},
            onMoveCChange = {},
            onMoveBChange = {},
            onSendMoveCommand = {},
            onCalibrate = {},
            onCancelCalibration = {},
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
