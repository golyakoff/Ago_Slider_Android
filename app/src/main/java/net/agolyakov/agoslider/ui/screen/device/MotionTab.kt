package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.ble.HomeStatus
import net.agolyakov.agoslider.data.model.scenario.ScenarioStatus
import net.agolyakov.agoslider.service.scenario.FocusScenarioManager
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

// ----------------------------------------------------------------------------
// Motion tab: high-level motion commands (currently only Home;
// the future "scenarios" mode will live here too — see TODO.md)
// ----------------------------------------------------------------------------
@Composable
fun MotionTabContent(
    homeStatus: HomeStatus,
    scenarioState: FocusScenarioManager.State,
    scenarioStatus: ScenarioStatus?,
    scenarioCoordinates: Triple<Float?, Float?, Float?>,
    xTravelLimit: Float,
    onSendHomeCommand: (Boolean, Boolean, Boolean) -> Unit,
    onJogScenarioX: (Float) -> Unit,
    onJogScenarioC: (Float) -> Unit,
    onJogScenarioB: (Float) -> Unit,
    onActivateScenarioPoint: (Int, Float) -> Unit,
    onDefineScenarioPoint: (Float) -> Unit,
    onScenarioGoToStart: () -> Unit,
    onResetScenario: () -> Unit,
    onStartScenario: (Float) -> Unit,
    onStopScenario: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardTitle(Icons.Default.Home, stringResource(R.string.motion_homing))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSendHomeCommand(true, false, false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.motion_home_x))
                    }
                    Button(
                        onClick = { onSendHomeCommand(false, true, false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.motion_home_c))
                    }
                    Button(
                        onClick = { onSendHomeCommand(false, false, true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.motion_home_b))
                    }
                }
                Button(
                    onClick = { onSendHomeCommand(true, true, true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.motion_home_all))
                }

                // Status sits on the same level as the buttons rather than in a card of its
                // own — it belongs to this card, and nesting one inside another says otherwise
                HomeStatusRow(stringResource(R.string.motion_home_requested), homeStatus.requested)
                HomeStatusRow(stringResource(R.string.motion_homed_status), homeStatus.homed)
            }
        }

        FocusScenarioCard(
            state = scenarioState,
            status = scenarioStatus,
            coordinates = scenarioCoordinates,
            xTravelLimit = xTravelLimit,
            onJogX = onJogScenarioX,
            onJogC = onJogScenarioC,
            onJogB = onJogScenarioB,
            onActivatePoint = onActivateScenarioPoint,
            onDefinePoint = onDefineScenarioPoint,
            onGoToStart = onScenarioGoToStart,
            onReset = onResetScenario,
            onStart = onStartScenario,
            onStop = onStopScenario
        )
    }
}

@Composable
private fun HomeStatusRow(label: String, state: Triple<Boolean, Boolean, Boolean>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AxisStateDot("X", state.first)
            AxisStateDot("C", state.second)
            AxisStateDot("B", state.third)
        }
    }
}

// ----------------------------------------------------------------------------
// Previews
// ----------------------------------------------------------------------------
@Composable
private fun MotionTabPreview(darkTheme: Boolean) {
    AgoSliderTheme(darkTheme) {
        MotionTabContent(
            homeStatus = HomeStatus(Triple(true, true, false), Triple(true, false, false)),
            scenarioState = FocusScenarioManager.State(),
            scenarioStatus = null,
            scenarioCoordinates = Triple(null, null, null),
            xTravelLimit = 780f,
            onSendHomeCommand = { _, _, _ -> },
            onJogScenarioX = {},
            onJogScenarioC = {},
            onJogScenarioB = {},
            onActivateScenarioPoint = { _, _ -> },
            onDefineScenarioPoint = {},
            onScenarioGoToStart = {},
            onResetScenario = {},
            onStartScenario = {},
            onStopScenario = {}
        )
    }
}

@Preview(name = "Light Theme", showBackground = true)
@Composable
private fun MotionTabLightPreview() {
    MotionTabPreview(darkTheme = false)
}

@Preview(name = "Dark Theme", showBackground = true)
@Composable
private fun MotionTabDarkPreview() {
    MotionTabPreview(darkTheme = true)
}
