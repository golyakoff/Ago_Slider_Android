@file:OptIn(ExperimentalMaterial3Api::class)

package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarmType
import net.agolyakov.agoslider.data.model.ble.AgoSliderTime
import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarm
import net.agolyakov.agoslider.ui.component.TimePickerDialogState
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme
import net.agolyakov.agoslider.ui.theme.timeHeadlineMedium
import java.time.LocalDateTime

@Composable
fun DeviceScreen(
    navController: NavHostController,
    device: AgoSliderDevice?
) {
    val viewModel: DeviceViewModel = hiltViewModel()
    val isOn: Boolean by viewModel.agoSliderOn.collectAsState()
    val manualBrightness: Byte by viewModel.agoSliderManualBrightness.collectAsState()
    val bleTime: AgoSliderTime by viewModel.agoSliderDeviceTime.collectAsState()
    var phoneTime: AgoSliderTime by remember { mutableStateOf(AgoSliderTime.now()) }
    val timePickerState: TimePickerDialogState by viewModel.timePickerState.collectAsState()
    val turnOnAlarm: AgoSliderAlarm by viewModel.agoSliderTurnOnAlarm.collectAsState()
    val turnOffAlarm: AgoSliderAlarm by viewModel.agoSliderTurnOffAlarm.collectAsState()
    val agingOffset: Int by viewModel.agoSliderAgingOffset.collectAsState()
    val rtcTemperature: Float by viewModel.agoSliderRtcTemperature.collectAsState()

    if (timePickerState.isVisible) {
        TimePickerDialog(
            initialHour = timePickerState.hour,
            initialMinute = timePickerState.minute,
            onDismiss = { viewModel.hideTimePickerDialog() },
            onTimeSelected = { hour, minute ->
                viewModel.setAlarmTime(
                    timePickerState.alarmType,
                    hour,
                    minute,
                    timePickerState.isActive
                )
                viewModel.hideTimePickerDialog()
            }
        )
    }

    LaunchedEffect(bleTime) {
        phoneTime = AgoSliderTime.now()
    }

    LaunchedEffect(device) {
        viewModel.connectToDevice(device)
        viewModel.bluetoothService.setShouldMaintainConnection(true)
    }

    DeviceSettings(
        deviceFriendlyName = device?.friendlyName ?: device?.deviceName ?: "<без имени>",
        deviceMacAddress = device?.macAddress ?: "<без адреса?",
        firmwareVersion = "v1.0.0",
        isOn = isOn,
        onSwitchOnOffCheckedChangeAction = { isChecked ->
            viewModel.toggleOnOffCharacteristic()
        },
        manualBrightness = manualBrightness,
        onSliderBrightnessValueChanged = { newValue ->
            viewModel.setManualBrightnessCharacteristic(newValue.toInt().toByte())
        },
        bleTime = bleTime,
        phoneTime = phoneTime,
        onButtonSyncClickAction = { viewModel.syncBleWithPhone() },
        agingOffset = agingOffset,
        onAgingOffsetDialogValueChanged = { newValue ->
            viewModel.setAgingOffsetCharacteristic(newValue)
        },
        rtcTemperature = rtcTemperature,
        turnOnAlarm = turnOnAlarm,
        turnOnAlarmOnTimeClick = {
            viewModel.showTimePickerDialog(AgoSliderAlarmType.TURN_ON, turnOnAlarm)
        },
        turnOnAlarmOnActiveToggle = {
            viewModel.toggleAlarmActive(AgoSliderAlarmType.TURN_ON)
        },
        turnOffAlarm = turnOffAlarm,
        turnOffAlarmOnTimeClick = {
            viewModel.showTimePickerDialog(AgoSliderAlarmType.TURN_OFF, turnOffAlarm)
        },
        turnOffAlarmOnActiveToggle = {
            viewModel.toggleAlarmActive(AgoSliderAlarmType.TURN_OFF)
        },
        onFirmwareUpdateButtonClickAction = {
            navController.navigate("firmware_update")
        }
    )
}

@Composable
fun DeviceSettings(
    deviceFriendlyName: String,
    deviceMacAddress: String,
    firmwareVersion: String,
    isOn: Boolean,
    onSwitchOnOffCheckedChangeAction: (Boolean) -> Unit,
    manualBrightness: Byte,
    onSliderBrightnessValueChanged: (Float) -> Unit,
    bleTime: AgoSliderTime,
    phoneTime: AgoSliderTime,
    onButtonSyncClickAction: () -> Unit,
    agingOffset: Int,
    onAgingOffsetDialogValueChanged: (Int) -> Unit,
    rtcTemperature: Float,
    turnOnAlarm: AgoSliderAlarm,
    turnOnAlarmOnTimeClick: () -> Unit,
    turnOnAlarmOnActiveToggle: () -> Unit,
    turnOffAlarm: AgoSliderAlarm,
    turnOffAlarmOnTimeClick: () -> Unit,
    turnOffAlarmOnActiveToggle: () -> Unit,
    onFirmwareUpdateButtonClickAction: () -> Unit
) {

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderCard(
            deviceFriendlyName,
            deviceMacAddress,
            firmwareVersion
        )

        BrightnessCard(
            modifier = Modifier,
            isOn,
            onSwitchOnOffCheckedChangeAction,
            manualBrightness,
            onSliderBrightnessValueChanged,
        )

        TimeSyncCard(
            modifier = Modifier,
            bleTime,
            phoneTime,
            onButtonSyncClickAction,
            agingOffset,
            onAgingOffsetDialogValueChanged
        )

        OnOffScenariosCard(
            modifier = Modifier,
            turnOnAlarm,
            turnOnAlarmOnTimeClick,
            turnOnAlarmOnActiveToggle,
            turnOffAlarm,
            turnOffAlarmOnTimeClick,
            turnOffAlarmOnActiveToggle
        )

        SystemInfoCard(
            modifier = Modifier,
            firmwareVersion,
            onFirmwareUpdateButtonClickAction,
            rtcTemperature
        )
    }
}

@Composable
fun HeaderCard(
    friendlyName: String,
    macAddress: String,
    firmwareVersion: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Matrix Clock",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = friendlyName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = macAddress,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = firmwareVersion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun BrightnessCard(
    modifier: Modifier,
    isOn: Boolean,
    onSwitchOnOffCheckedChangeAction: (Boolean) -> Unit,
    manualBrightness: Byte,
    onSliderBrightnessValueChanged: (Float) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            CardTitle(
                Icons.Default.Brightness6,
                "${stringResource(R.string.mc_manual_brightness)} ${manualBrightness + 1}",
                true,
                isOn,
                onSwitchOnOffCheckedChangeAction
            )

            BrightnessSlider(
                isOn,
                manualBrightness,
                onSliderBrightnessValueChanged,
            )
        }
    }
}

@Composable
fun BrightnessSlider(
    isOn: Boolean,
    manualBrightness: Byte,
    onSliderBrightnessValueChanged: (Float) -> Unit,
) {
    Slider(
        value = manualBrightness.toFloat(),
        onValueChange = onSliderBrightnessValueChanged,
        valueRange = 0f..15f,
        steps = 14,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        enabled = isOn
    )
}

@Composable
fun TimeSyncCard(
    modifier: Modifier = Modifier,
    bleTime: AgoSliderTime,
    phoneTime: AgoSliderTime,
    onButtonSyncClickAction: () -> Unit,
    agingOffset: Int,
    onAgingOffsetDialogValueChanged: (Int) -> Unit,
) {
    var showAgingOffsetDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CardTitle(
                Icons.Default.Adjust,
                "Точность хода")

            // Карточки времени в строку
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TimeCard(
                    title = stringResource(R.string.mc_time_of_ago_slider),
                    time = bleTime,
                    modifier = Modifier.weight(1f)
                )

                TimeCard(
                    title = stringResource(R.string.mc_time_of_mobile_phone),
                    time = phoneTime,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            CardButtonWide(
                Icons.Default.SwapCalls,
                stringResource(R.string.mc_aging_offset),
                { showAgingOffsetDialog = true},
                false
            )

            AgingOffsetDialog(
                agingOffset,
                showAgingOffsetDialog,
                onDismiss = { showAgingOffsetDialog = false },
                onValueConfirmed = { newValue ->
                    showAgingOffsetDialog = false
                    onAgingOffsetDialogValueChanged(newValue)
                }
            )

            Spacer(Modifier.height(12.dp))

            CardButtonWide(
                Icons.Default.Sync,
                stringResource(R.string.mc_action_synchronize_time),
                onButtonSyncClickAction,
                true
            )
        }
    }
}

@Composable
fun TimeCard(
    title: String,
    time: AgoSliderTime,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor =MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = time.formatTimeHours(),
                    style = MaterialTheme.typography.timeHeadlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = time.formatTimeMinutes(),
                    style = MaterialTheme.typography.timeHeadlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = time.formatTimeSeconds(),
                    style = MaterialTheme.typography.timeHeadlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = time.formatDayAndMonth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = time.formatYear(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun AgingOffsetDialog(
    currentValue: Int,
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onValueConfirmed: (Int) -> Unit
) {
    if (showDialog) {
        var inputValue by remember(currentValue) { mutableStateOf(currentValue.toString()) }

        AlertDialog(
            title = { Text(stringResource( R.string.mc_dialog_set_aging_offset)) },
            text = {
                Column()
                {
                    Text(text = stringResource(R.string.mc_dialog_agin_offset_tip1))

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = {
                            inputValue = it.filter { char -> char.isDigit() || char == '-' }
                        },
                        label = { Text("Значение от -128 до 127") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            onDismissRequest = onDismiss,
            confirmButton = {
                Button(
                    onClick = {
                        val newValue = inputValue.toIntOrNull()?.coerceIn(-128..127)
                        newValue?.let { onValueConfirmed(it) }
                    }
                ) {
                    Text(stringResource(R.string.mc_dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.mc_dialog_cancel))
                }
            }
        )
    }
}

@Composable
fun OnOffScenariosCard(
    modifier: Modifier,
    turnOnAlarm: AgoSliderAlarm,
    turnOnAlarmOnTimeClick: () -> Unit,
    turnOnAlarmOnActiveToggle: () -> Unit,
    turnOffAlarm: AgoSliderAlarm,
    turnOffAlarmOnTimeClick: () -> Unit,
    turnOffAlarmOnActiveToggle: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            CardTitle(
                Icons.Default.Timelapse,
                "Сценарии")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AlarmItem(
                    modifier = Modifier.weight(1f),
                    labelTop = "Авто", // = stringResource(R.string.mc_check_auto_on),
                    labelBottom = "ВКЛ",
                    alarm = turnOnAlarm,
                    onTimeClick = turnOnAlarmOnTimeClick,
                    onActiveToggle = turnOnAlarmOnActiveToggle
                )
                AlarmItem(
                    modifier = Modifier.weight(1f),
                    labelTop = "Авто", // stringResource(R.string.mc_check_auto_off),
                    labelBottom = "ВЫКЛ",
                    alarm = turnOffAlarm,
                    onTimeClick = turnOffAlarmOnTimeClick,
                    onActiveToggle = turnOffAlarmOnActiveToggle
                )
            }
        }
    }
}

@Composable
fun AlarmItem(
    modifier: Modifier = Modifier,
    labelTop: String,
    labelBottom: String,
    alarm: AgoSliderAlarm,
    onTimeClick: () -> Unit,
    onActiveToggle: () -> Unit
) {
    val color = if (alarm.isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column {
                    Text(
                        text = labelTop,
                        color = color,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)

                    Text(
                        text = labelBottom,
                        color = color,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }

                Switch(
                    checked = alarm.isActive,
                    onCheckedChange = {
                            _ -> onActiveToggle()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            AlarmTimeField(
                alarm.hours,
                alarm.minutes,
                isActive = alarm.isActive,
                onFieldClick = onTimeClick
            )
        }

    }
}

@Composable
fun AlarmTimeField(
    alarmHours: Byte,
    alarmMinutes: Byte,
    isActive: Boolean,
    onFieldClick: () -> Unit
) {
    val color = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = isActive,
                onClick = onFieldClick
            )
            .border(
                width = 1.dp,
                color = color,
                shape = MaterialTheme.shapes.medium
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                text = "%02d".format(alarmHours),
                style = MaterialTheme.typography.timeHeadlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "%02d".format(alarmMinutes),
                style = MaterialTheme.typography.timeHeadlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.mc_dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.mc_dialog_cancel))
            }
        },
        title = { Text(stringResource(R.string.mc_action_set_time)) },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

@Composable
fun SystemInfoCard(
    modifier: Modifier,
    firmwareVersion: String,
    onFirmwareUpdateButtonClickAction: () -> Unit,
    rtcTemperature: Float
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            CardTitle(
                Icons.Default.PermDeviceInformation,
                "Система"
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "${stringResource(R.string.mc_rtc_temperature)}: ${
                    if (rtcTemperature > 0) "+" else ""
                }${"%.2f".format(rtcTemperature)} ${stringResource(R.string.mc_degrees_celsius)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(8.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Версия прошивки часов: " + firmwareVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(16.dp))

            CardButtonWide(
                Icons.Default.SystemUpdateAlt,
                "Обновление прошивки",
                onFirmwareUpdateButtonClickAction,
                false
            )
        }
    }
}

@Composable
fun CardTitle(
    imageVector: ImageVector,
    text: String,
    showSwitch: Boolean = false,
    isOn: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Row (
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    )
    {
        Row (
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                text = text
            )
        }

        if(showSwitch)
        {
            Switch(
                checked = isOn,
                onCheckedChange = onCheckedChange
            )
        }
    }

    Spacer(Modifier.height(16.dp))
}

@Composable
fun CardButtonWide(
    imageVector: ImageVector,
    text: String,
    onClickAction: () -> Unit,
    isPrimary: Boolean = false
) {
    val containerColor =
        if (isPrimary)  MaterialTheme.colorScheme.primary
        else  MaterialTheme.colorScheme.surfaceVariant

    val contentColor =
        if (isPrimary)  MaterialTheme.colorScheme.onPrimary
        else  MaterialTheme.colorScheme.onSurfaceVariant

    Button(
        onClick = onClickAction,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = text,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
@Preview(
    name = "State 1: Light Theme",
    showBackground = true,
    heightDp = 1200
)
fun DeviceSettings_State1_Preview(){
    AgoSliderTheme (
        darkTheme = false
    ) {
        DeviceSettings(
            deviceFriendlyName = "Часы в детской",
            deviceMacAddress = "1a:2b:3c:4d:5e:6f",
            firmwareVersion = "v1.0.1-rc6",
            isOn = true,
            onButtonSyncClickAction = {},
            manualBrightness = 8,
            onSliderBrightnessValueChanged = {},
            bleTime = AgoSliderTime(
                LocalDateTime.now()
                    .minusHours(1)
                    .minusMinutes(1)
                    .minusSeconds(1)
            ),
            phoneTime = AgoSliderTime(LocalDateTime.now()),
            onSwitchOnOffCheckedChangeAction = {},
            agingOffset = 5,
            onAgingOffsetDialogValueChanged = {},
            rtcTemperature = 21.25F,
            turnOnAlarm = AgoSliderAlarm(
                isActive = true,
                hours = 6,
                minutes = 15),
            turnOnAlarmOnTimeClick = {},
            turnOnAlarmOnActiveToggle = {},
            turnOffAlarm = AgoSliderAlarm(
                isActive = false,
                hours = 23,
                minutes = 45),
            turnOffAlarmOnTimeClick = {},
            turnOffAlarmOnActiveToggle = {},
            onFirmwareUpdateButtonClickAction = {}
        )
    }
}

@Composable
@Preview(
    name = "State 2: Dark Theme",
    showBackground = true,
    heightDp = 1200)
fun DeviceSettings_State2_Preview(){
    AgoSliderTheme (
        darkTheme = true
    ) {
        DeviceSettings(
            deviceFriendlyName = "Ago Slider",
            deviceMacAddress = "11:22:33:44:55:66",
            firmwareVersion = "v.1.0.0",
            isOn = false,
            onButtonSyncClickAction = {},
            manualBrightness = 3,
            onSliderBrightnessValueChanged = {},
            bleTime = AgoSliderTime(
                LocalDateTime.now()
                    .minusHours(1)
                    .minusMinutes(1)
                    .minusSeconds(1)
            ),
            phoneTime = AgoSliderTime(LocalDateTime.now()),
            onSwitchOnOffCheckedChangeAction = {},
            agingOffset = -108,
            onAgingOffsetDialogValueChanged = {},
            rtcTemperature = -10.25F,
            turnOnAlarm = AgoSliderAlarm(
                isActive = false,
                hours = 6,
                minutes = 15),
            turnOnAlarmOnTimeClick = {},
            turnOnAlarmOnActiveToggle = {},
            turnOffAlarm = AgoSliderAlarm(
                isActive = true,
                hours = 23,
                minutes = 45),
            turnOffAlarmOnTimeClick = {},
            turnOffAlarmOnActiveToggle = {},
            onFirmwareUpdateButtonClickAction = {}
        )
    }
}
