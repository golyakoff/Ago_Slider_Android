package net.agolyakov.agoslider.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ControlCamera
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.navigation.Screen
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

@Composable
fun HomeScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel
) {
    val devices by homeViewModel.devices.observeAsState(emptyList())

    var showRenameDialog by remember { mutableStateOf(false) }
    var deviceToRename by remember { mutableStateOf<AgoSliderDevice?>(null) }

    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        DeviceList(devices, navController, onRenameClick = { device ->
            deviceToRename = device
            showRenameDialog = true
        })
    }

    if (showRenameDialog && deviceToRename != null) {
        RenameDeviceDialog(
            device = deviceToRename!!,
            onDismiss = {
                showRenameDialog = false
                deviceToRename = null
            },
            onRename = { newName ->
                homeViewModel.renameDevice(deviceToRename!!.macAddress, newName)
            }
        )
    }
}

@Composable
fun DeviceList(
    deviceList: List<AgoSliderDevice>,
    navController: NavHostController,
    onRenameClick: (AgoSliderDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        itemsIndexed(deviceList) { _, item ->
            Device(item, navController, onRenameClick)
        }
    }
}

@Composable
fun Device(
    device: AgoSliderDevice,
    navController: NavHostController,
    onRenameClick: (AgoSliderDevice) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .padding(16.dp, 16.dp, 16.dp, 0.dp)
            .clickable {
                navController.currentBackStackEntry?.savedStateHandle?.set(
                    key = "device",
                    value = device
                )
                navController.navigate(Screen.Device.route)
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 24.dp)
        ) {
            Icon(
                Icons.Outlined.ControlCamera,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.getDisplayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(
                onClick = { onRenameClick(device) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.rename),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun RenameDeviceDialog(
    device: AgoSliderDevice,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(device.friendlyName ?: device.deviceName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_device)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.dialog_device_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onRename(text)
                    } else {
                        onRename("")
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

private val previewDeviceList = listOf(
    AgoSliderDevice(
        deviceName = "AGO Slider",
        macAddress = "11:22:33:44:55:66"
    ),
    AgoSliderDevice(
        deviceName = "AGO Slider",
        macAddress = "11:22:33:44:55:77",
        friendlyName = "Слайдер в студии"
    )
)

@Composable
@Preview(name = "Light Schema", heightDp = 800, showBackground = false)
fun DeviceListPreview_1() {
    AgoSliderTheme(darkTheme = false) {
        DeviceList(previewDeviceList, rememberNavController()) {}
    }
}

@Composable
@Preview(name = "Dark Schema", heightDp = 800, showBackground = true)
fun DeviceListPreview_2() {
    AgoSliderTheme(darkTheme = true) {
        DeviceList(previewDeviceList, rememberNavController()) {}
    }
}
