package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.agolyakov.agoslider.data.model.scenario.ScenarioStatus
import net.agolyakov.agoslider.service.scenario.FocusScenarioManager
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sets up and watches a focus pass.
 *
 * The run itself belongs to the device, so this card is only a remote: once started it can be
 * left, and whatever it shows on return comes from the slider's own status rather than from
 * anything remembered here.
 *
 * There are no text fields anywhere: every value is either dialled on a slider or measured by
 * aiming. A keyboard would cover the very jog buttons the user is aiming with.
 */
@Composable
fun FocusScenarioCard(
    state: FocusScenarioManager.State,
    status: ScenarioStatus?,
    coordinates: Triple<Float?, Float?, Float?>,
    xTravelLimit: Float,
    onJogX: (Float) -> Unit,
    onJogC: (Float) -> Unit,
    onJogB: (Float) -> Unit,
    onActivatePoint: (Int, Float) -> Unit,
    onDefinePoint: (Float) -> Unit,
    onGoToStart: () -> Unit,
    onReset: () -> Unit,
    onStart: (seconds: Float) -> Unit,
    onStop: () -> Unit
) {
    // Keyed on the limit so the default follows the calibrated rail once the settings load
    var xTravel by remember(xTravelLimit) { mutableStateOf(xTravelLimit) }
    var durationRange by remember { mutableStateOf(DurationRange.SECONDS) }
    var duration by remember { mutableStateOf(60f) }   // always held in seconds
    var confirmDefine by remember { mutableStateOf(false) }

    val running = status?.state == ScenarioStatus.State.RUNNING

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardTitle(Icons.Default.CenterFocusStrong, stringResource(R.string.scenario_focus_title))

            if (running) {
                RunningBlock(status, onStop)
                return@Column
            }

            Text(
                stringResource(R.string.scenario_focus_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // ---- parameters ---------------------------------------------------
            Text(
                stringResource(R.string.scenario_x_travel_value, fmt(xTravel)),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = xTravel.coerceIn(-xTravelLimit, xTravelLimit),
                onValueChange = { xTravel = (it / X_TRAVEL_STEP).roundToInt() * X_TRAVEL_STEP },
                valueRange = -xTravelLimit..xTravelLimit,
                modifier = Modifier.fillMaxWidth()
            )

            // One slider cannot usefully span seconds to hours, so the range is picked first
            // and the slider then works at a resolution that means something inside it
            Text(
                stringResource(R.string.scenario_duration_label, formatDuration(duration)),
                style = MaterialTheme.typography.labelMedium
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                for (range in DurationRange.entries) {
                    val select = {
                        durationRange = range
                        duration = duration.coerceIn(range.seconds)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .selectable(selected = durationRange == range, onClick = select)
                    ) {
                        RadioButton(
                            selected = durationRange == range,
                            onClick = select,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            stringResource(range.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 3.dp)
                        )
                    }
                }
            }
            Slider(
                value = duration.coerceIn(durationRange.seconds),
                onValueChange = { duration = (it / durationRange.step).roundToInt() * durationRange.step },
                valueRange = durationRange.seconds,
                modifier = Modifier.fillMaxWidth()
            )

            // ---- the three aimed points ---------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (index in 0..2) {
                    AimPointBlock(
                        index = index,
                        point = state.points[index],
                        active = state.activeIndex == index,
                        enabled = state.canActivate(index),
                        onClick = { onActivatePoint(index, xTravel) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---- jogging ------------------------------------------------------
            JogRow(
                label = stringResource(R.string.scenario_jog_x_value, axisValue(coordinates.first, 1)),
                steps = listOf(-100f, -10f, -1f, 1f, 10f, 100f),
                onJog = onJogX
            )
            JogRow(
                label = stringResource(R.string.scenario_jog_c_value, axisValue(coordinates.second, 1)),
                steps = listOf(-10f, -2f, -0.5f, 0.5f, 2f, 10f),
                onJog = onJogC
            )
            JogRow(
                label = stringResource(R.string.scenario_jog_b_value, axisValue(coordinates.third, 1)),
                steps = listOf(-10f, -2f, -0.5f, 0.5f, 2f, 10f),
                onJog = onJogB
            )

            Button(onClick = { confirmDefine = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scenario_define_point, state.activeIndex + 1))
            }

            state.solution?.let { solution ->
                Text(
                    text = stringResource(
                        R.string.scenario_solved,
                        fmt(solution.subjectAlongRail),
                        fmt(solution.distance / 10f),
                        fmt(solution.residualDeg, 2)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                // The fit says how far the three readings are from describing one subject; a
                // loose fit means the pass will track no better than that
                if (solution.residualDeg > 0.5f) {
                    Text(
                        text = stringResource(R.string.scenario_solved_poor, fmt(solution.residualDeg, 2)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            FocusScenarioManager.State.Error.NOT_READY -> R.string.scenario_error_not_ready
                            FocusScenarioManager.State.Error.UNSUPPORTED -> R.string.scenario_error_unsupported
                            FocusScenarioManager.State.Error.NEED_THREE_POINTS -> R.string.scenario_error_need_points
                            FocusScenarioManager.State.Error.INCONSISTENT -> R.string.scenario_error_inconsistent
                            FocusScenarioManager.State.Error.REFUSED -> R.string.scenario_error_refused
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            status?.let { if (it.state != ScenarioStatus.State.IDLE) OutcomeLine(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = state.definedCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.scenario_reset))
                }
                OutlinedButton(
                    onClick = onGoToStart,
                    enabled = state.points[0] != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.scenario_go_to_start))
                }
            }
            Button(
                onClick = { onStart(duration) },
                enabled = state.allDefined,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.scenario_start))
            }
        }
    }

    // Defining sits one tap away from the jog buttons the user is aiming with, so it asks first
    if (confirmDefine) {
        AlertDialog(
            onDismissRequest = { confirmDefine = false },
            title = { Text(stringResource(R.string.scenario_define_confirm_title, state.activeIndex + 1)) },
            text = { Text(stringResource(R.string.scenario_define_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDefine = false
                    onDefinePoint(xTravel)
                }) { Text(stringResource(R.string.scenario_define_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDefine = false }) {
                    Text(stringResource(R.string.scenario_define_confirm_no))
                }
            }
        )
    }
}

@Composable
private fun AimPointBlock(
    index: Int,
    point: FocusScenarioManager.AimPoint?,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dash = stringResource(R.string.value_unknown)
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        // The active block is the one the next "define" will fill, so it carries the accent;
        // the tick says something different — that a block already holds a measurement
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null
    ) {
        val ink = if (active) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
        Column(modifier = Modifier.padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.scenario_point_n, index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink
                )
                if (point != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(13.dp)
                    )
                }
            }
            AxisLine("X", point?.xUnits?.let { fmt(it) } ?: dash, ink)
            AxisLine("C", point?.cUnits?.let { fmt(it, 1) + "°" } ?: dash, ink)
            AxisLine("B", point?.bUnits?.let { fmt(it, 1) + "°" } ?: dash, ink)
        }
    }
}

@Composable
private fun AxisLine(axis: String, value: String, ink: androidx.compose.ui.graphics.Color) {
    Text("$axis $value", style = MaterialTheme.typography.labelSmall, color = ink)
}

@Composable
private fun JogRow(label: String, steps: List<Float>, onJog: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (step in steps) {
            OutlinedButton(
                onClick = { onJog(step) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(1.dp)
            ) {
                Text(
                    text = if (step > 0) "+${trimZero(step)}" else trimZero(step),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RunningBlock(status: ScenarioStatus, onStop: () -> Unit) {
    Text(
        stringResource(R.string.scenario_running, formatDuration(status.remainingMs / 1000f)),
        style = MaterialTheme.typography.bodyMedium
    )
    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scenario_stop))
    }
}

@Composable
private fun OutcomeLine(status: ScenarioStatus) {
    val headline = when (status.state) {
        ScenarioStatus.State.DONE -> stringResource(R.string.scenario_state_done)
        ScenarioStatus.State.ABORTED -> stringResource(R.string.scenario_state_aborted)
        ScenarioStatus.State.FAILED -> stringResource(R.string.scenario_state_failed)
        else -> return
    }
    val why = when (status.reason) {
        ScenarioStatus.Reason.USER_STOP -> stringResource(R.string.scenario_reason_user_stop)
        ScenarioStatus.Reason.LIMIT -> stringResource(R.string.scenario_reason_limit)
        ScenarioStatus.Reason.MOTORS_OFF -> stringResource(R.string.scenario_reason_motors_off)
        ScenarioStatus.Reason.NOT_HOMED -> stringResource(R.string.scenario_reason_not_homed)
        ScenarioStatus.Reason.BUSY -> stringResource(R.string.scenario_reason_busy)
        ScenarioStatus.Reason.BAD_PARAMS -> stringResource(R.string.scenario_reason_bad_params)
        ScenarioStatus.Reason.UNKNOWN_ID -> stringResource(R.string.scenario_reason_unknown_id)
        else -> null
    }
    Text(
        text = if (why != null) "$headline — $why" else headline,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

/** Seconds to hours on one slider would spend its whole travel on the short end. */
private enum class DurationRange(
    val seconds: ClosedFloatingPointRange<Float>,
    val step: Float,
    val labelRes: Int
) {
    SECONDS(1f..60f, 1f, R.string.scenario_range_seconds),
    MINUTES(120f..3600f, 60f, R.string.scenario_range_minutes),
    LONG(3660f..7200f, 60f, R.string.scenario_range_long)
}

private const val X_TRAVEL_STEP = 10f

@Composable
private fun axisValue(value: Float?, decimals: Int) =
    value?.let { fmt(it, decimals) } ?: stringResource(R.string.value_unknown)

private fun fmt(value: Float, decimals: Int = 0) =
    String.format(Locale.US, "%.${decimals}f", value)

private fun trimZero(value: Float) =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

/** Nothing user-facing is hardcoded, units included. */
@Composable
private fun formatDuration(seconds: Float): String {
    val total = seconds.roundToInt()
    return if (total < 60) {
        stringResource(R.string.scenario_dur_seconds, total)
    } else {
        stringResource(R.string.scenario_dur_min_sec, total / 60, total % 60)
    }
}
