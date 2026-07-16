package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

// ----------------------------------------------------------------------------
// Service tab: low-level per-axis relative moves for checking that the motors
// work, plus raw hardware status (limit switches, power monitor)
// ----------------------------------------------------------------------------
@Composable
fun ServiceTabContent(
    moveX: Int,
    moveC: Int,
    moveB: Int,
    limitStatus: Triple<Boolean, Boolean, Boolean>,
    powerInfo: Triple<Float, Float, Float>,
    powerInfoString: String,
    onMoveXChange: (Int) -> Unit,
    onMoveCChange: (Int) -> Unit,
    onMoveBChange: (Int) -> Unit,
    onSendMoveCommand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Move command
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Move relative (steps)", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = moveX.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onMoveXChange) },
                        label = { Text("X") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = moveC.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onMoveCChange) },
                        label = { Text("C") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = moveB.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onMoveBChange) },
                        label = { Text("B") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(onClick = onSendMoveCommand) { Text("Move") }
            }
        }

        HorizontalDivider()

        // Read-only hardware status
        Text("Limit switches: X=${limitStatus.first}, C=${limitStatus.second}, B=${limitStatus.third}")
        Text("Power: ${powerInfo.first}V, ${powerInfo.second}A, ${powerInfo.third}W")
        Text("Power string: $powerInfoString")
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
            moveC = 0,
            moveB = 0,
            limitStatus = Triple(false, true, false),
            powerInfo = Triple(21.48f, 0.082f, 1.76f),
            powerInfoString = "21.48V 0.082A 1.76W",
            onMoveXChange = {},
            onMoveCChange = {},
            onMoveBChange = {},
            onSendMoveCommand = {}
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
