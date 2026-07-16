package net.agolyakov.agoslider.ui.screen.device

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.ble.ConnectionState
import net.agolyakov.agoslider.data.model.ble.HomeStatus
import net.agolyakov.agoslider.navigation.Screen
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

// ----------------------------------------------------------------------------
// Tabs of the device screen, grouped by purpose:
//  - Motion:   high-level motion commands (Home; scenarios mode in the future)
//  - Service:  low-level per-axis moves and raw hardware status
//  - Settings: axis/driver configuration
// ----------------------------------------------------------------------------
enum class DeviceTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Motion(R.string.device_tab_motion, Icons.Filled.ControlCamera),
    Service(R.string.device_tab_service, Icons.Filled.Build),
    Settings(R.string.device_tab_settings, Icons.Filled.Settings)
}

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

    // Name the device in the action bar instead of spending a content row on it
    val context = LocalContext.current
    val deviceName = device?.getDisplayName()
    DisposableEffect(deviceName) {
        val activity = context as? Activity
        if (deviceName != null) {
            activity?.title = context.getString(R.string.device_title, deviceName)
        }
        onDispose {
            activity?.title = context.getString(R.string.app_full_name)
        }
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

    val connectionStateString = when (val state = connectionState) {
        is ConnectionState.Connecting -> stringResource(R.string.state_connecting)
        is ConnectionState.Connected -> stringResource(R.string.state_connected)
        is ConnectionState.Ready -> stringResource(R.string.state_ready)
        is ConnectionState.Disconnecting -> stringResource(R.string.state_disconnecting)
        is ConnectionState.Disconnected -> stringResource(R.string.state_disconnected)
        is ConnectionState.Error -> stringResource(R.string.state_error, state.message)
    }

    DeviceScreenContent(
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
        onInvertDirChange = viewModel::setInvertDir,
        onCheckFirmwareUpdates = { navController.navigate(Screen.FirmwareUpdate.route) }
    )
}

// ----------------------------------------------------------------------------
// Pure UI component (previewable): shared header + bottom tab navigation
// ----------------------------------------------------------------------------
@Composable
fun DeviceScreenContent(
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
    onInvertDirChange: (Boolean, Boolean, Boolean) -> Unit,
    onCheckFirmwareUpdates: () -> Unit = {},
    initialTab: DeviceTab = DeviceTab.Motion
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                DeviceTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            DeviceHeader(
                connectionStateString = connectionStateString,
                firmwareVersion = firmwareVersion,
                batteryLevel = batteryLevel,
                motorsEnabled = motorsEnabled,
                onMotorsEnabledChange = onMotorsEnabledChange
            )

            HorizontalDivider()

            when (selectedTab) {
                DeviceTab.Motion -> MotionTabContent(
                    homeStatus = homeStatus,
                    onSendHomeCommand = onSendHomeCommand
                )

                DeviceTab.Service -> ServiceTabContent(
                    moveX = moveX,
                    moveC = moveC,
                    moveB = moveB,
                    axisUnit = axisUnit,
                    limitStatus = limitStatus,
                    powerInfo = powerInfo,
                    powerInfoString = powerInfoString,
                    firmwareVersion = firmwareVersion,
                    onMoveXChange = onMoveXChange,
                    onMoveCChange = onMoveCChange,
                    onMoveBChange = onMoveBChange,
                    onSendMoveCommand = onSendMoveCommand,
                    onCheckFirmwareUpdates = onCheckFirmwareUpdates
                )

                DeviceTab.Settings -> SettingsTabContent(
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
                    onMicrostepsChange = onMicrostepsChange,
                    onRunCurrentChange = onRunCurrentChange,
                    onHoldCurrentChange = onHoldCurrentChange,
                    onAxisUnitChange = onAxisUnitChange,
                    onUnitsPerStepChange = onUnitsPerStepChange,
                    onAxisSpeedChange = onAxisSpeedChange,
                    onAxisAccelChange = onAxisAccelChange,
                    onVirtualLimitChange = onVirtualLimitChange,
                    onStealthChopChange = onStealthChopChange,
                    onInvertDirChange = onInvertDirChange
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Shared header: device identity, connection status and the global motor
// enable switch — visible on every tab
// ----------------------------------------------------------------------------
@Composable
private fun DeviceHeader(
    connectionStateString: String,
    firmwareVersion: String,
    batteryLevel: Int,
    motorsEnabled: Boolean,
    onMotorsEnabledChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // The device name is not repeated here — it is in the action bar title
        // Three equal columns, so a field changing width (connection state, version)
        // cannot shift the others sideways
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderStatusField(
                text = connectionStateString,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            HeaderStatusField(
                text = stringResource(R.string.device_firmware, firmwareVersion),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            HeaderStatusField(
                text = stringResource(R.string.device_battery, batteryLevel * 100 / 255),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.device_motors_enabled), modifier = Modifier.weight(1f))
            Switch(checked = motorsEnabled, onCheckedChange = onMotorsEnabledChange)
        }
    }
}

@Composable
private fun HeaderStatusField(
    text: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ----------------------------------------------------------------------------
// Previews
// ----------------------------------------------------------------------------
@Composable
fun DeviceScreenPreview(darkTheme: Boolean, initialTab: DeviceTab = DeviceTab.Motion) {
    AgoSliderTheme(darkTheme) {
        DeviceScreenContent(
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
            onInvertDirChange = { _, _, _ -> },
            initialTab = initialTab
        )
    }
}

@Preview(name = "Motion / Light", showBackground = true)
@Composable
fun DeviceScreenMotionLightPreview() {
    DeviceScreenPreview(darkTheme = false, initialTab = DeviceTab.Motion)
}

@Preview(name = "Motion / Dark", showBackground = true)
@Composable
fun DeviceScreenMotionDarkPreview() {
    DeviceScreenPreview(darkTheme = true, initialTab = DeviceTab.Motion)
}

@Preview(name = "Service / Light", showBackground = true)
@Composable
fun DeviceScreenServiceLightPreview() {
    DeviceScreenPreview(darkTheme = false, initialTab = DeviceTab.Service)
}

@Preview(name = "Settings / Light", showBackground = true, heightDp = 1600)
@Composable
fun DeviceScreenSettingsLightPreview() {
    DeviceScreenPreview(darkTheme = false, initialTab = DeviceTab.Settings)
}
