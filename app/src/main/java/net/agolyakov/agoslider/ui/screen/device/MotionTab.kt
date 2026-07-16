package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.ble.HomeStatus
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

// ----------------------------------------------------------------------------
// Motion tab: high-level motion commands (currently only Home;
// the future "scenarios" mode will live here too — see TODO.md)
// ----------------------------------------------------------------------------
@Composable
fun MotionTabContent(
    homeStatus: HomeStatus,
    onSendHomeCommand: (Boolean, Boolean, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.motion_homing), style = MaterialTheme.typography.titleMedium)
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

        // Home status table
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.motion_home_requested),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = axesState(homeStatus.requested),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.motion_homed_status),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = axesState(homeStatus.homed),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun axesState(state: Triple<Boolean, Boolean, Boolean>): String {
    val yes = stringResource(R.string.answer_yes)
    val no = stringResource(R.string.answer_no)
    return stringResource(
        R.string.motion_axes_state,
        if (state.first) yes else no,
        if (state.second) yes else no,
        if (state.third) yes else no
    )
}

// ----------------------------------------------------------------------------
// Previews
// ----------------------------------------------------------------------------
@Composable
private fun MotionTabPreview(darkTheme: Boolean) {
    AgoSliderTheme(darkTheme) {
        MotionTabContent(
            homeStatus = HomeStatus(Triple(true, true, false), Triple(true, false, false)),
            onSendHomeCommand = { _, _, _ -> }
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
