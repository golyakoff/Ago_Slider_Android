package net.agolyakov.agoslider.ui.viewmodel

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun MyRequestPermission(
    permissions: List<String>,
    onPermissionsResult: (allGranted: Boolean) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        onPermissionsResult(allGranted)
    }

    // Проверка и автозапуск
    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onPermissionsResult(true)
        } else {
            launcher.launch(permissions.toTypedArray())
        }
    }
}
