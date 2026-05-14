package net.agolyakov.agoslider.ui.screen.firmware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.agolyakov.agoslider.data.repository.FirmwareRepository
import net.agolyakov.agoslider.ui.screen.firmware.NoUpdateScreenInternal
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

@Composable
fun FirmwareScreen(
    viewModel: FirmwareViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is FirmwareRepository.UpdateState.Idle -> {
                    IdleScreen(viewModel)
                }

                is FirmwareRepository.UpdateState.Checking -> {
                    CheckingScreen()
                }

                is FirmwareRepository.UpdateState.Processing -> {
                    ProcessingScreen(progress, statusMessage)
                }

                is FirmwareRepository.UpdateState.UpdateAvailable -> {
                    UpdateAvailableScreen(currentState, viewModel, onBack)
                }

                is FirmwareRepository.UpdateState.NoUpdate -> {
                    NoUpdateScreen(currentState, onBack)
                }

                is FirmwareRepository.UpdateState.Downloading -> {
                    DownloadingScreen(progress, statusMessage)
                }

                is FirmwareRepository.UpdateState.ReadyToInstall -> {
                    ReadyToInstallScreen(currentState, viewModel)
                }

                is FirmwareRepository.UpdateState.Installing -> {
                    InstallingScreen(progress, statusMessage)
                }

                is FirmwareRepository.UpdateState.Success -> {
                    SuccessScreen(onBack)
                }

                is FirmwareRepository.UpdateState.Error -> {
                    ErrorScreen(currentState, viewModel, onBack)
                }
            }
        }
    }
}

@Composable
private fun IdleScreen(viewModel: FirmwareViewModel) {
    IdleScreenInternal(
        { viewModel.checkForUpdates() }
    )
}

@Composable
private fun IdleScreenInternal(
    onCheckForUpdates: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Обновление прошивки",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCheckForUpdates,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = "Искать обновления",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CheckingScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            strokeWidth = 10.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Проверяем доступность обновлений...",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UpdateAvailableScreen(
    state: FirmwareRepository.UpdateState.UpdateAvailable,
    viewModel: FirmwareViewModel,
    onBack: () -> Unit
) {
    UpdateAvailableScreenInternal(
        state.currentVersion,
        state.release.tagName,
        { viewModel.performCompleteUpdate() },
        onBack
    )
}

@Composable
private fun UpdateAvailableScreenInternal(
    currentFirmwareVersion: String,
    newFirmwareVersion: String,
    onInstall: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Доступно обновление!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Текущая версия: ${currentFirmwareVersion}",
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Новая версия: ${newFirmwareVersion}",
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = "Обновить",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
               Text(
                   text = "Назад",
                   style = MaterialTheme.typography.titleMedium
               )
        }
    }
}

@Composable
private fun NoUpdateScreen(
    state: FirmwareRepository.UpdateState.NoUpdate,
    onBack: () -> Unit
) {
    NoUpdateScreenInternal(
        state.currentVersion,
        onBack
    )
}

@Composable
private fun NoUpdateScreenInternal(
    currentFirmwareVersion: String,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "У вас самая актуальная прошивка",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Версия: ${currentFirmwareVersion}",
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary),
            onClick = onBack
        ) {
            Text(
                text = "Назад",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun DownloadingScreen(
    progress: Float,
    statusMessage: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            strokeWidth = 10.dp,
            progress = { progress / 100f })

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Скачивание: ${"%.1f".format(progress)}%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ReadyToInstallScreen(
    state: FirmwareRepository.UpdateState.ReadyToInstall,
    viewModel: FirmwareViewModel
) {
    ReadyToInstallScreenInternal(
        firmwareVersion = state.release.tagName,
        firmwareSize = state.file.length(),
        onInstall = { viewModel.installFirmware(state.file) }
    )
}

@Composable
private fun ReadyToInstallScreenInternal(
    firmwareVersion: String,
    firmwareSize: Long,
    onInstall: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Прошивка готова",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Версия: ${firmwareVersion}",
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Размер: ${firmwareSize / 1024} KB",
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onInstall
        ) {
            Text(
                text = "Установить прошивку",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}


@Composable
private fun InstallingScreen(
    progress: Float,
    statusMessage: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            strokeWidth = 10.dp,
            progress = { progress / 100f })

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Установка: ${"%.1f".format(progress)}%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProcessingScreen(
    progress: Float,
    statusMessage: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            strokeWidth = 10.dp,
            progress = { progress / 100f })

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            style = MaterialTheme.typography.titleMedium,
            text = "Выполняется обновление...",
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text ="${"%.1f".format(progress)}%",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun SuccessScreen(onBack: () -> Unit) {
    SuccessScreenInternal(
        "Обновление завершено!",
        onBack
    )
}

@Composable
private fun SuccessScreenInternal(
    message: String,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary),
            onClick = onBack
        ) {
            Text(
                text = "Готово",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    state: FirmwareRepository.UpdateState.Error,
    viewModel: FirmwareViewModel,
    onBack: () -> Unit
) {
    ErrorScreenInternal(
        state.message,
        { viewModel.checkForUpdates() },
        onBack
    )
}

@Composable
private fun ErrorScreenInternal(
    message: String,
    onRepeat: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ошибка",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )

        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRepeat,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = "Попробовать снова",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(
                text = "Назад",
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
@Preview(
    name = "Error: Light Theme",
    showBackground = true,
    heightDp = 300
)
fun ErrorScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ErrorScreenInternal(
                "Сообщение об ошибке!",
                {},
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "Error: Dark Theme",
    showBackground = true,
    heightDp = 300
)
fun ErrorScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ErrorScreenInternal(
                "Сообщение об ошибке!",
                {},
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "Success: Light Theme",
    showBackground = true,
    heightDp = 250
)
fun SuccessScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            SuccessScreenInternal(
                "Обновление завершено!",
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "Success: Dark Theme",
    showBackground = true,
    heightDp = 250
)
fun SuccessScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            SuccessScreenInternal(
                "Обновление завершено!",
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "Processing: Light Theme",
    showBackground = true,
    heightDp = 200
)
fun ProcessingScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ProcessingScreen(
                 71f,
                 "Идёт обработка"
            )
        }
    }
}

@Composable
@Preview(
    name = "Processing: Dark Theme",
    showBackground = true,
    heightDp = 200
)
fun ProcessingScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ProcessingScreen(
                23.52f,
                "Идёт обработка"
            )
        }
    }
}



@Composable
@Preview(
    name = "Installing: Light Theme",
    showBackground = true,
    heightDp = 200
)
fun InstallingScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            InstallingScreen(
                71f,
                "Идёт установка"
            )
        }
    }
}

@Composable
@Preview(
    name = "Installing: Dark Theme",
    showBackground = true,
    heightDp = 200
)
fun InstallingScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            InstallingScreen(
                23.52f,
                "Идёт установка"
            )
        }
    }
}

@Composable
@Preview(
    name = "ReadyToInstall: Light Theme",
    showBackground = true,
    heightDp = 250
)
fun ReadyToInstallScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ReadyToInstallScreenInternal(
                "v2.5.92-rc21",
                2387822,
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "ReadyToInstall: Dark Theme",
    showBackground = true,
    heightDp = 250
)
fun ReadyToInstallScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ReadyToInstallScreenInternal(
                "v1.5.92",
                1938782,
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "Installing: Light Theme",
    showBackground = true,
    heightDp = 200
)
fun DownloadingScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DownloadingScreen(
                71f,
                "Идёт установка"
            )
        }
    }
}

@Composable
@Preview(
    name = "Installing: Dark Theme",
    showBackground = true,
    heightDp = 200
)
fun DownloadingScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DownloadingScreen(
                23.52f,
                "Идёт установка"
            )
        }
    }
}

@Composable
@Preview(
    name = "NoUpdateScreen: Light Theme",
    showBackground = true,
    heightDp = 300
)
fun NoUpdateScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            NoUpdateScreenInternal(
                "v2.5.92-rc21",
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "NoUpdateScreen: Dark Theme",
    showBackground = true,
    heightDp = 300
)
fun NoUpdateScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            NoUpdateScreenInternal(
                "v1.5.92",
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "UpdateAvailableScreen: Light Theme",
    showBackground = true,
    heightDp = 300
)
fun UpdateAvailableScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            UpdateAvailableScreenInternal(
                "v2.5.92-rc21",
                "v3.0.0",
                {},
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "UpdateAvailableScreen: Dark Theme",
    showBackground = true,
    heightDp = 300
)
fun UpdateAvailableScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            UpdateAvailableScreenInternal(
                "v1.5.92",
                "v2.1.0",
                {},
                {}
            )
        }
    }
}

@Composable
@Preview(
    name = "CheckingScreen: Light Theme",
    showBackground = true,
    heightDp = 300
)
fun CheckingScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            CheckingScreen()
        }
    }
}

@Composable
@Preview(
    name = "CheckingScreen: Dark Theme",
    showBackground = true,
    heightDp = 300
)
fun CheckingScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            CheckingScreen()
        }
    }
}

@Composable
@Preview(
    name = "IdleScreen: Light Theme",
    showBackground = true,
    heightDp = 300
)
fun IdleScreen_Light_Preview() {
    AgoSliderTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            IdleScreenInternal ({})
        }
    }
}

@Composable
@Preview(
    name = "IdleScreen: Dark Theme",
    showBackground = true,
    heightDp = 300
)
fun IdleScreen_Dark_Preview() {
    AgoSliderTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            IdleScreenInternal({})
        }
    }
}
