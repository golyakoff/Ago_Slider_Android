package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.scenario.ScenarioStatus
import net.agolyakov.agoslider.service.scenario.FocusScenarioManager
import java.util.Locale

/**
 * Sets up and watches a focus pass.
 *
 * The run itself belongs to the device, so this card is only a remote: once started it can be
 * left, and whatever it shows on return comes from the slider's own status rather than from
 * anything remembered here.
 */
@Composable
fun FocusScenarioCard(
    state: FocusScenarioManager.State,
    status: ScenarioStatus?,
    xPosition: Float?,
    onJogC: (Float) -> Unit,
    onJogX: (Float) -> Unit,
    onMarkAim: (Float) -> Unit,
    onClearAims: () -> Unit,
    onStart: (xTravel: Float, bTravel: Float, seconds: Float, distanceOverrideMm: Float?) -> Unit,
    onStop: () -> Unit
) {
    var xTravel by remember { mutableStateOf("300") }
    var bTravel by remember { mutableStateOf("0") }
    var duration by remember { mutableStateOf("60") }
    var distanceCm by remember { mutableStateOf("") }

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

            // ---- aiming -------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.scenario_aim_step, (state.aims.size + 1).coerceAtMost(3)),
                    style = MaterialTheme.typography.labelLarge
                )
                // A stale number for an axis that is no longer anchored would read as fact
                Text(
                    stringResource(
                        R.string.scenario_x_position,
                        xPosition?.let { fmt(it, 1) } ?: stringResource(R.string.value_unknown)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // Placing the carriage is part of aiming, so the controls for it belong here
            // rather than a tab away
            Text(
                stringResource(R.string.scenario_jog_x),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (step in listOf(-100f, -10f, -1f, 1f, 10f, 100f)) {
                    OutlinedButton(
                        onClick = { onJogX(step) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(1.dp)
                    ) {
                        Text(
                            text = if (step > 0) "+${fmt(step)}" else fmt(step),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.scenario_jog_c),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (step in listOf(-10f, -2f, -0.5f, 0.5f, 2f, 10f)) {
                    OutlinedButton(
                        onClick = { onJogC(step) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)
                    ) {
                        Text(
                            text = if (step > 0) "+${trimZero(step)}" else trimZero(step),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onMarkAim(xTravel.toFloatOrNull() ?: 0f) },
                    enabled = state.aims.size < 3,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.scenario_aim_mark))
                }
                OutlinedButton(
                    onClick = onClearAims,
                    enabled = state.aims.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.scenario_aim_redo))
                }
            }

            // The recorded marks stay on screen: they are the raw evidence behind the solved
            // geometry, so a suspicious result can be checked against what was actually aimed
            if (state.aims.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.aims.forEachIndexed { index, aim ->
                        Text(
                            text = stringResource(
                                R.string.scenario_aim_recorded,
                                index + 1, fmt(aim.xUnits, 1), fmt(aim.cUnits, 2)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            state.solution?.let { solution ->
                Text(
                    text = stringResource(
                        R.string.scenario_solved,
                        fmt(solution.subjectAlongRail),
                        fmt(solution.distance / 10f),
                        fmt(solution.residualDeg, 2)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // The fit is how far the three readings are from describing one subject; a
                // loose fit means the pass will track no better than that
                if (solution.residualDeg > 0.5f) {
                    Text(
                        text = stringResource(R.string.scenario_solved_poor, fmt(solution.residualDeg, 2)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ---- the pass -----------------------------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(xTravel, { xTravel = it }, R.string.scenario_x_travel, Modifier.weight(1f))
                NumberField(bTravel, { bTravel = it }, R.string.scenario_b_travel, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(duration, { duration = it }, R.string.scenario_duration, Modifier.weight(1f))
                NumberField(distanceCm, { distanceCm = it }, R.string.scenario_distance_manual, Modifier.weight(1f))
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

            Button(
                onClick = {
                    onStart(
                        xTravel.toFloatOrNull() ?: 0f,
                        bTravel.toFloatOrNull() ?: 0f,
                        duration.toFloatOrNull() ?: 0f,
                        distanceCm.toFloatOrNull()?.let { it * 10f }
                    )
                },
                enabled = state.solution != null || distanceCm.toFloatOrNull() != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.scenario_start))
            }
        }
    }
}

@Composable
private fun RunningBlock(status: ScenarioStatus, onStop: () -> Unit) {
    Text(
        stringResource(R.string.scenario_running, formatSeconds(status.remainingMs)),
        style = MaterialTheme.typography.bodyMedium
    )
    LinearProgressIndicator(
        progress = { status.progress },
        modifier = Modifier.fillMaxWidth()
    )
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

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun fmt(value: Float, decimals: Int = 0) =
    String.format(Locale.US, "%.${decimals}f", value)

/** Whole degrees read better without a trailing ".0" on a button this narrow. */
private fun trimZero(value: Float) =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

private fun formatSeconds(ms: Long): String {
    val total = ms / 1000
    return if (total >= 60) "${total / 60}:${String.format(Locale.US, "%02d", total % 60)}"
    else "${total}s"
}
