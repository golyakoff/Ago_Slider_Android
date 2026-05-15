package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.ble.HomeStatus

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

    // Move command UI state
    val moveX by viewModel.moveX.collectAsState()
    val moveC by viewModel.moveC.collectAsState()
    val moveB by viewModel.moveB.collectAsState()

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
        Text(text = "Connection state: $connectionState")
        Text(text = "Battery level: ${batteryLevel * 100 / 255}% ($batteryLevel/255)")

        Divider()

        // Motor enable
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Motors enabled", modifier = Modifier.weight(1f))
            Switch(checked = motorsEnabled, onCheckedChange = viewModel::setMotorsEnabled)
        }

        // Home command (simple buttons)
        Text("Homing", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.sendHomeCommand(true, false, false) }) { Text("Home X") }
            Button(onClick = { viewModel.sendHomeCommand(false, true, false) }) { Text("Home C") }
            Button(onClick = { viewModel.sendHomeCommand(false, false, true) }) { Text("Home B") }
            Button(onClick = { viewModel.sendHomeCommand(true, true, true) }) { Text("Home All") }
        }
        Text("Home status: requested=${homeStatus.requested}, homed=${homeStatus.homed}")

        // Move command
        Text("Move relative (steps)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = moveX.toString(), onValueChange = { it.toIntOrNull()?.let(viewModel::updateMoveX) }, label = { Text("X") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = moveC.toString(), onValueChange = { it.toIntOrNull()?.let(viewModel::updateMoveC) }, label = { Text("C") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = moveB.toString(), onValueChange = { it.toIntOrNull()?.let(viewModel::updateMoveB) }, label = { Text("B") }, modifier = Modifier.weight(1f))
        }
        Button(onClick = viewModel::sendMoveCommand) { Text("Move") }

        Divider()

        // Microsteps
        ConfigTriple("Microsteps (1,2,4,8,16,32,64,128,256)", microsteps, viewModel::setMicrosteps)
        // Run current
        ConfigTriple("Run current (mA)", runCurrent, viewModel::setRunCurrent)
        // Hold current
        ConfigTriple("Hold current (mA)", holdCurrent, viewModel::setHoldCurrent)
        // Axis unit
        BoolTriple("Axis unit (true=deg, false=mm)", axisUnit, viewModel::setAxisUnit)
        // Units per step
        FloatTriple("Units per step (mm/step or deg/step)", unitsPerStep, viewModel::setUnitsPerStep)
        // Axis speed
        ConfigTriple("Axis speed (steps/sec)", axisSpeed, viewModel::setAxisSpeed)
        // Axis acceleration
        ConfigTriple("Axis accel (steps/sec²)", axisAccel, viewModel::setAxisAccel)
        // Virtual limit
        BoolTriple("Virtual limit enabled", virtualLimit, viewModel::setVirtualLimit)
        // StealthChop
        BoolTriple("StealthChop enabled", stealthChop, viewModel::setStealthChop)
        // Invert direction
        BoolTriple("Invert direction", invertDir, viewModel::setInvertDir)

        // Limit status (read-only)
        Text("Limit switches: X=${limitStatus.first}, C=${limitStatus.second}, B=${limitStatus.third}")

        // Power info
        Text("Power: ${powerInfo.first}V, ${powerInfo.second}A, ${powerInfo.third}W")
        Text("Power string: ${viewModel.powerInfoString.collectAsState().value}")
    }
}

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