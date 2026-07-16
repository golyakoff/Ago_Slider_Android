package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.ble.HomeStatus
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import net.agolyakov.agoslider.data.model.ble.ConnectionState

// ----------------------------------------------------------------------------
// Public screen (uses Hilt ViewModel)
// ----------------------------------------------------------------------------
@Composable
fun DeviceScreen(
    navController: NavHostController,
    device: AgoSliderDevice?
) {
    val viewModel: DeviceViewModel = hiltViewModel()

    LaunchedEffect(device) {
        viewModel.connectToDevice(device)
    }

    // Collect state flows
    val connectionState by viewModel.connectionState.collectAsState()
    val motorsEnabled by viewModel.motorsEnabled.collectAsState()
    val microsteps by viewModel.microsteps.collectAsState()
    val runCurrent by viewModel.runCurrent.collectAsState()
    val holdCurrent by viewModel.holdCurrent.collectAsState()
    val axisUnit by viewModel.axisUnit.collectAsState()
    val unitsPerStep by viewModel.unitsPerStep.collectAsState()
    val axisSpeed by viewModel.axisSpeed.collectAsState()
    val axisAccel by viewModel.axisAccel.collectAsState()
    val virtualLimit by viewModel.virtualLimit.collectAsState()
    val stealthChop by viewModel.stealthChop.collectAsState()
    val invertDir by viewModel.invertDir.collectAsState()
    val limitStatus by viewModel.limitStatus.collectAsState()
    val homeStatus by viewModel.homeStatus.collectAsState()
    val powerInfo by viewModel.powerInfo.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val firmwareVersion by viewModel.firmwareVersion.collectAsState()
    val powerInfoString by viewModel.powerInfoString.collectAsState()

    val moveX by viewModel.moveX.collectAsState()
    val moveC by viewModel.moveC.collectAsState()
    val moveB by viewModel.moveB.collectAsState()

    val connectionStateString = when (connectionState) {
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Ready -> "Ready"
        is ConnectionState.Disconnecting -> "Disconnecting"
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Error -> "Error: ${(connectionState as ConnectionState.Error).message}"
        else -> connectionState.toString()
    }

    DeviceScreenContent(
        device = device,
        connectionStateString = connectionStateString,
        firmwareVersion = firmwareVersion,
        batteryLevel = batteryLevel,
        motorsEnabled = motorsEnabled,
        microsteps = microsteps,
        runCurrent = runCurrent,
        holdCurrent = holdCurrent,
        axisUnit = axisUnit,
        unitsPerStep = unitsPerStep,
        axisSpeed = axisSpeed,
        axisAccel = axisAccel,
        virtualLimit = virtualLimit,
        stealthChop = stealthChop,
        invertDir = invertDir,
        limitStatus = limitStatus,
        homeStatus = homeStatus,
        powerInfo = powerInfo,
        powerInfoString = powerInfoString,
        moveX = moveX,
        moveC = moveC,
        moveB = moveB,
        onMotorsEnabledChange = viewModel::setMotorsEnabled,
        onSendHomeCommand = viewModel::sendHomeCommand,
        onSendMoveCommand = viewModel::sendMoveCommand,
        onMoveXChange = viewModel::updateMoveX,
        onMoveCChange = viewModel::updateMoveC,
        onMoveBChange = viewModel::updateMoveB,
        onMicrostepsChange = viewModel::setMicrosteps,
        onRunCurrentChange = viewModel::setRunCurrent,
        onHoldCurrentChange = viewModel::setHoldCurrent,
        onAxisUnitChange = viewModel::setAxisUnit,
        onUnitsPerStepChange = viewModel::setUnitsPerStep,
        onAxisSpeedChange = viewModel::setAxisSpeed,
        onAxisAccelChange = viewModel::setAxisAccel,
        onVirtualLimitChange = viewModel::setVirtualLimit,
        onStealthChopChange = viewModel::setStealthChop,
        onInvertDirChange = viewModel::setInvertDir
    )
}

// ----------------------------------------------------------------------------
// Pure UI component (previewable)
// ----------------------------------------------------------------------------
@Composable
fun DeviceScreenContent(
    device: AgoSliderDevice?,
    connectionStateString: String,
    firmwareVersion: String,
    batteryLevel: Int,
    motorsEnabled: Boolean,
    microsteps: Triple<Int, Int, Int>,
    runCurrent: Triple<Int, Int, Int>,
    holdCurrent: Triple<Int, Int, Int>,
    axisUnit: Triple<Boolean, Boolean, Boolean>,
    unitsPerStep: Triple<Float, Float, Float>,
    axisSpeed: Triple<Int, Int, Int>,
    axisAccel: Triple<Int, Int, Int>,
    virtualLimit: Triple<Boolean, Boolean, Boolean>,
    stealthChop: Triple<Boolean, Boolean, Boolean>,
    invertDir: Triple<Boolean, Boolean, Boolean>,
    limitStatus: Triple<Boolean, Boolean, Boolean>,
    homeStatus: HomeStatus,
    powerInfo: Triple<Float, Float, Float>,
    powerInfoString: String,
    moveX: Int,
    moveC: Int,
    moveB: Int,
    onMotorsEnabledChange: (Boolean) -> Unit,
    onSendHomeCommand: (Boolean, Boolean, Boolean) -> Unit,
    onSendMoveCommand: () -> Unit,
    onMoveXChange: (Int) -> Unit,
    onMoveCChange: (Int) -> Unit,
    onMoveBChange: (Int) -> Unit,
    onMicrostepsChange: (Int, Int, Int) -> Unit,
    onRunCurrentChange: (Int, Int, Int) -> Unit,
    onHoldCurrentChange: (Int, Int, Int) -> Unit,
    onAxisUnitChange: (Boolean, Boolean, Boolean) -> Unit,
    onUnitsPerStepChange: (Float, Float, Float) -> Unit,
    onAxisSpeedChange: (Int, Int, Int) -> Unit,
    onAxisAccelChange: (Int, Int, Int) -> Unit,
    onVirtualLimitChange: (Boolean, Boolean, Boolean) -> Unit,
    onStealthChopChange: (Boolean, Boolean, Boolean) -> Unit,
    onInvertDirChange: (Boolean, Boolean, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "Device: ${device?.getDisplayName() ?: "Unknown"}",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(text = "MAC: ${device?.macAddress ?: "N/A"}")
        Text(text = "Firmware: $firmwareVersion")
        Text(text = "Connection state: $connectionStateString")
        Text(text = "Battery level: ${batteryLevel * 100 / 255}% ($batteryLevel/255)")

        Divider()

        // Motor enable
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Motors enabled", modifier = Modifier.weight(1f))
            Switch(checked = motorsEnabled, onCheckedChange = onMotorsEnabledChange)
        }

        // Home command
        Text("Homing", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSendHomeCommand(true, false, false) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Home X")
            }
            Button(
                onClick = { onSendHomeCommand(false, true, false) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Home C")
            }
            Button(
                onClick = { onSendHomeCommand(false, false, true) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Home B")
            }
        }
        Button(
            onClick = { onSendHomeCommand(true, true, true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Home All")
        }
        // Home status table
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Row 1: Home Requested
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Home Requested:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "X=${if (homeStatus.requested.first) "YES" else "NO"}, " +
                                "C=${if (homeStatus.requested.second) "YES" else "NO"}, " +
                                "B=${if (homeStatus.requested.third) "YES" else "NO"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Row 2: Homed Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Homed Status:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "X=${if (homeStatus.homed.first) "YES" else "NO"}, " +
                                "C=${if (homeStatus.homed.second) "YES" else "NO"}, " +
                                "B=${if (homeStatus.homed.third) "YES" else "NO"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

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
        Divider()

        // Config sections
        ConfigTriple("Microsteps (1,2,4,8,16,32,64,128,256)", microsteps, onMicrostepsChange)
        ConfigTriple("Run current (mA)", runCurrent, onRunCurrentChange)
        ConfigTriple("Hold current (mA)", holdCurrent, onHoldCurrentChange)
        BoolTriple("Axis unit (true=deg, false=mm)", axisUnit, onAxisUnitChange)
        FloatTriple("Units per step (mm/step or deg/step)", unitsPerStep, onUnitsPerStepChange)
        ConfigTriple("Axis speed (steps/sec)", axisSpeed, onAxisSpeedChange)
        ConfigTriple("Axis accel (steps/sec²)", axisAccel, onAxisAccelChange)
        BoolTriple("Virtual limit enabled", virtualLimit, onVirtualLimitChange)
        BoolTriple("StealthChop enabled", stealthChop, onStealthChopChange)
        BoolTriple("Invert direction", invertDir, onInvertDirChange)

        // Read-only status
        Text("Limit switches: X=${limitStatus.first}, C=${limitStatus.second}, B=${limitStatus.third}")
        Text("Power: ${powerInfo.first}V, ${powerInfo.second}A, ${powerInfo.third}W")
        Text("Power string: $powerInfoString")
    }
}

// ----------------------------------------------------------------------------
// Reusable configuration components
// ----------------------------------------------------------------------------
@Composable
fun ConfigTriple(
    title: String,
    values: Triple<Int, Int, Int>,
    onValueChange: (Int, Int, Int) -> Unit
) {
    var x by remember(values.first) { mutableStateOf(values.first.toString()) }
    var c by remember(values.second) { mutableStateOf(values.second.toString()) }
    var b by remember(values.third) { mutableStateOf(values.third.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = x, onValueChange = { x = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("C") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("B") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val xi = x.toIntOrNull() ?: values.first
                val ci = c.toIntOrNull() ?: values.second
                val bi = b.toIntOrNull() ?: values.third
                onValueChange(xi, ci, bi)
            }) { Text("Set") }
        }
    }
}

@Composable
fun FloatTriple(
    title: String,
    values: Triple<Float, Float, Float>,
    onValueChange: (Float, Float, Float) -> Unit
) {
    var x by remember(values.first) { mutableStateOf(values.first.toString()) }
    var c by remember(values.second) { mutableStateOf(values.second.toString()) }
    var b by remember(values.third) { mutableStateOf(values.third.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = x, onValueChange = { x = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("C") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("B") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val xf = x.toFloatOrNull() ?: values.first
                val cf = c.toFloatOrNull() ?: values.second
                val bf = b.toFloatOrNull() ?: values.third
                onValueChange(xf, cf, bf)
            }) { Text("Set") }
        }
    }
}

@Composable
fun BoolTriple(
    title: String,
    values: Triple<Boolean, Boolean, Boolean>,
    onValueChange: (Boolean, Boolean, Boolean) -> Unit
) {
    var x by remember(values.first) { mutableStateOf(values.first) }
    var c by remember(values.second) { mutableStateOf(values.second) }
    var b by remember(values.third) { mutableStateOf(values.third) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row { Text("X: "); Switch(checked = x, onCheckedChange = { x = it }) }
                Row { Text("C: "); Switch(checked = c, onCheckedChange = { c = it }) }
                Row { Text("B: "); Switch(checked = b, onCheckedChange = { b = it }) }
            }
            Button(onClick = { onValueChange(x, c, b) }) { Text("Set") }
        }
    }
}

// ----------------------------------------------------------------------------
// Previews
// ----------------------------------------------------------------------------
@Composable
fun DeviceScreenPreview(darkTheme: Boolean) {
    AgoSliderTheme(darkTheme) {
        DeviceScreenContent(
            device = AgoSliderDevice(
                deviceName = "AGO Slider",
                macAddress = "AA:BB:CC:DD:EE:FF",
                friendlyName = "Test Device"
            ),
            connectionStateString = "Connected",
            firmwareVersion = "v1.0.0",
            batteryLevel = 200,
            motorsEnabled = true,
            microsteps = Triple(16, 16, 16),
            runCurrent = Triple(900, 700, 900),
            holdCurrent = Triple(450, 350, 450),
            axisUnit = Triple(false, true, true),
            unitsPerStep = Triple(0.19195f, 0.2125f, 0.29268f),
            axisSpeed = Triple(1000, 1000, 1000),
            axisAccel = Triple(1000, 1000, 1000),
            virtualLimit = Triple(true, false, true),
            stealthChop = Triple(true, true, true),
            invertDir = Triple(false, false, false),
            limitStatus = Triple(false, false, false),
            homeStatus = HomeStatus(Triple(false, false, false), Triple(false, false, false)),
            powerInfo = Triple(21.48f, 0.082f, 1.76f),
            powerInfoString = "21.48V 0.082A 1.76W",
            moveX = 0,
            moveC = 0,
            moveB = 0,
            onMotorsEnabledChange = {},
            onSendHomeCommand = { _, _, _ -> },
            onSendMoveCommand = {},
            onMoveXChange = {},
            onMoveCChange = {},
            onMoveBChange = {},
            onMicrostepsChange = { _, _, _ -> },
            onRunCurrentChange = { _, _, _ -> },
            onHoldCurrentChange = { _, _, _ -> },
            onAxisUnitChange = { _, _, _ -> },
            onUnitsPerStepChange = { _, _, _ -> },
            onAxisSpeedChange = { _, _, _ -> },
            onAxisAccelChange = { _, _, _ -> },
            onVirtualLimitChange = { _, _, _ -> },
            onStealthChopChange = { _, _, _ -> },
            onInvertDirChange = { _, _, _ -> }
        )
    }
}

@Preview(name = "Light Theme", showBackground = true, heightDp = 2000)
@Composable
fun DeviceScreenLightPreview() {
    DeviceScreenPreview(darkTheme = false)
}

@Preview(name = "Dark Theme", showBackground = true, heightDp = 2000)
@Composable
fun DeviceScreenDarkPreview() {
    DeviceScreenPreview(darkTheme = true)
}