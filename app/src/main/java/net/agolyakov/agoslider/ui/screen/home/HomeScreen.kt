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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.repository.AppUpdateRepository
import net.agolyakov.agoslider.navigation.Screen
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

@Composable
fun HomeScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel
) {
    val devices by homeViewModel.devices.observeAsState(emptyList())
    val appUpdate by homeViewModel.appUpdate.collectAsState()
    val uriHandler = LocalUriHandler.current

    var showRenameDialog by remember { mutableStateOf(false) }
    var deviceToRename by remember { mutableStateOf<AgoSliderDevice?>(null) }

    HomeContent(
        devices = devices,
        appVersion = homeViewModel.appVersion,
        appUpdate = appUpdate,
        navController = navController,
        onRenameClick = { device ->
            deviceToRename = device
            showRenameDialog = true
        },
        onDownloadUpdateClick = { uriHandler.openUri(it.releaseUrl) }
    )

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
private fun HomeContent(
    devices: List<AgoSliderDevice>,
    appVersion: String,
    appUpdate: AppUpdateRepository.AppUpdate?,
    navController: NavHostController,
    onRenameClick: (AgoSliderDevice) -> Unit,
    onDownloadUpdateClick: (AppUpdateRepository.AppUpdate) -> Unit
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        DeviceList(
            deviceList = devices,
            navController = navController,
            modifier = Modifier.weight(1f),
            onRenameClick = onRenameClick
        )
        AppVersionCard(
            version = appVersion,
            update = appUpdate,
            onDownloadClick = onDownloadUpdateClick
        )
    }
}

@Composable
fun DeviceList(
    deviceList: List<AgoSliderDevice>,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onRenameClick: (AgoSliderDevice) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
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

// Always shows the installed version; the update line and the download button appear only
// once HomeViewModel's periodic check finds a newer release. Downloading is manual —
// the button just opens the release page in a browser.
@Composable
private fun AppVersionCard(
    version: String,
    update: AppUpdateRepository.AppUpdate?,
    onDownloadClick: (AppUpdateRepository.AppUpdate) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_version, version),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (update != null) {
                    Text(
                        text = stringResource(R.string.app_update_available, update.version),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (update != null) {
                Spacer(Modifier.width(8.dp))

                Button(onClick = { onDownloadClick(update) }) {
                    Text(stringResource(R.string.app_update_download))
                }
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

private val previewAppUpdate = AppUpdateRepository.AppUpdate(
    version = "v0.2.0",
    releaseUrl = "https://github.com/golyakoff/Ago_Slider_Android/releases/tag/v0.2.0"
)

@Composable
private fun HomeContentPreview(darkTheme: Boolean, appUpdate: AppUpdateRepository.AppUpdate?) {
    AgoSliderTheme(darkTheme) {
        HomeContent(
            devices = previewDeviceList,
            appVersion = "v0.1.1",
            appUpdate = appUpdate,
            navController = rememberNavController(),
            onRenameClick = {},
            onDownloadUpdateClick = {}
        )
    }
}

@Composable
@Preview(name = "Light Schema", heightDp = 800, showBackground = false)
fun DeviceListPreview_1() {
    HomeContentPreview(darkTheme = false, appUpdate = null)
}

@Composable
@Preview(name = "Dark Schema, update available", heightDp = 800, showBackground = true)
fun DeviceListPreview_2() {
    HomeContentPreview(darkTheme = true, appUpdate = previewAppUpdate)
}
