package net.agolyakov.agoslider.data.repository

import android.content.Context
import net.agolyakov.agoslider.utils.HashUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.agolyakov.agoslider.data.model.ble.ConnectionState
import net.agolyakov.agoslider.data.model.github.GithubAsset
import net.agolyakov.agoslider.data.model.github.GithubRelease
import net.agolyakov.agoslider.service.bluetooth.BluetoothService
import net.agolyakov.agoslider.service.bluetooth.AgoSliderManager.Companion.OTA_CMD_START
import net.agolyakov.agoslider.service.bluetooth.AgoSliderManager.Companion.OTA_CMD_END
import net.agolyakov.agoslider.service.bluetooth.AgoSliderManager.Companion.OTA_CMD_ABORT
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class FirmwareRepository @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val githubRepository: GithubRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    sealed class UpdateResult {
        data class Success(val oldVersion: String, val newVersion: String) : UpdateResult()
        data class NoUpdateAvailable(val currentVersion: String) : UpdateResult()
        data class Error(val message: String, val cause: Exception? = null) : UpdateResult()
        object Cancelled : UpdateResult()
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        object Processing : UpdateState()
        data class UpdateAvailable(val release: GithubRelease, val currentVersion: String) : UpdateState()
        data class NoUpdate(val currentVersion: String) : UpdateState()
        data class Downloading(val progress: Float) : UpdateState()
        data class ReadyToInstall(val file: File, val release: GithubRelease) : UpdateState()
        data class Installing(val progress: Float) : UpdateState()
        object Success : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    suspend fun getCurrentVersion(): String {
        return bluetoothService.getCurrentVersion()
    }

    suspend fun checkForUpdates(includePreReleases: Boolean = false): UpdateState {
        val currentVersion = getCurrentVersion()
        val latestRelease = githubRepository.getLatestRelease(includePreReleases)
        return if (isNewVersionAvailable(currentVersion, latestRelease.tagName)) {
            UpdateState.UpdateAvailable(latestRelease, currentVersion)
        } else {
            UpdateState.NoUpdate(currentVersion)
        }
    }

    suspend fun downloadFirmware(
        release: GithubRelease,
        onProgress: (Float) -> Unit = {}
    ): UpdateState {
        return withContext(Dispatchers.IO) {
            try {
                val asset = release.assets.first { it.name.endsWith("_release_4mb_fw.bin") }
                val firmwareFile = downloadFirmwareAsset(asset, onProgress)
                validateFirmware(firmwareFile, asset)
                UpdateState.ReadyToInstall(firmwareFile, release)
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    suspend fun installFirmware(
        firmwareFile: File,
        onProgress: (Float) -> Unit
    ): UpdateState {
        return withContext(Dispatchers.IO) {
            try {
                installFirmwareInternal(firmwareFile, onProgress)
                UpdateState.Success
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Installation failed")
            }
        }
    }

    suspend fun performCompleteUpdate(
        includePreReleases: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            // 1. check existing version
            onProgress(0f, "Получение текущей версии...")
            val currentVersion = getCurrentVersion()

            // 2. check for updates available
            onProgress(1f, "Проверка наличия обновлений...")
            val updateState = checkForUpdates(includePreReleases)
            if (updateState is UpdateState.NoUpdate) {
                return@withContext UpdateResult.NoUpdateAvailable(currentVersion)
            }

            val release = (updateState as UpdateState.UpdateAvailable).release

            // 3. download new firmware
            onProgress(2f, "Скачивание прошивки...")
            val downloadResult = downloadFirmware(release) { downloadProgress ->
                val overallProgress = 2f + downloadProgress * 0.03f
                onProgress(overallProgress, "Скачивание прошивки...")
            }

            if (downloadResult is UpdateState.Error) {
                return@withContext UpdateResult.Error(downloadResult.message)
            }

            val firmwareFile = (downloadResult as UpdateState.ReadyToInstall).file

            // 4. install new firmware
            onProgress(5f, "Установка обновления...")
            val installResult = installFirmware(firmwareFile) { installProgress ->
                val overallProgress = 5f + installProgress * 0.94f
                onProgress(overallProgress, "Установка обновления...")
            }

            if (installResult is UpdateState.Error) {
                return@withContext UpdateResult.Error(installResult.message)
            }

            // 5. validate installed firmware and reboot
            onProgress(99f, "Перезагрузка устройства...")
            bluetoothService.disconnect()

            delay(5000)

            var retries = 0
            while (retries < 20) {
                try {
                    if (bluetoothService.connectionState.value != ConnectionState.Connected) {
                        bluetoothService.tryReconnect()
                        delay(2000)
                    }

                    val uploadedFirmwareVersion = updateState.release.tagName
                    val realDeviceFirmwareVersion = getCurrentVersion()
                    if (realDeviceFirmwareVersion == uploadedFirmwareVersion) {
                        onProgress(100f, "Обновление завершено!")
                        return@withContext UpdateResult.Success(currentVersion, realDeviceFirmwareVersion)
                    }
                    delay(1000)
                    retries++
                } catch (e: Exception) {
                    delay(1000)
                    retries++
                }
            }

            UpdateResult.Error("Не могу проверить корректность установки!")

        } catch (e: CancellationException) {
            abortOta()
            UpdateResult.Cancelled
        } catch (e: Exception) {
            abortOta()
            UpdateResult.Error("Обновление завершилось ошибкой: ${e.message}", e)
        }
    }

    suspend fun abortOta() {
        bluetoothService.exitOtaUpdateMode()
        val success = bluetoothService.sendOtaControl(
            byteArrayOf(OTA_CMD_ABORT)
        )
        if (!success) {
            throw IOException("Failed to abort OTA process")
        }
    }

    private suspend fun downloadFirmwareAsset(
        asset: GithubAsset,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val responseBody = githubRepository.downloadAsset(asset.id.toString())
        val contentLength = responseBody.contentLength()
        val inputStream = responseBody.byteStream()

        val outputFile = File(context.cacheDir, "firmware_${System.currentTimeMillis()}.bin")
        FileOutputStream(outputFile).use { outputStream ->
            val buffer = ByteArray(8192)
            var totalRead: Long = 0
            var bytesRead: Int
            var lastProgress = -1f

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                if (contentLength > 0) {
                    val progress = (totalRead.toFloat() / contentLength.toFloat()) * 100f
                    if (progress - lastProgress >= 1f || progress >= 100f) {
                        lastProgress = progress
                        withContext(Dispatchers.Main) {
                            onProgress(progress.coerceIn(0f, 100f))
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            onProgress(100f)
        }

        outputFile
    }

    private suspend fun validateFirmware(file: File, asset: GithubAsset) {
        if (file.length() != asset.size) {
            throw IOException("File size mismatch: expected ${asset.size}, got ${file.length()}")
        }

        asset.sha256Hash?.let { expectedHash ->
            if (!HashUtils.verifyFileHash(file, expectedHash)) {
                throw IOException("SHA256 hash mismatch")
            }
        }
    }

    private suspend fun installFirmwareInternal(
        firmwareFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        var inputStream: FileInputStream? = null
        try {
            bluetoothService.enterOtaUpdateMode();
            val startCommand = createStartCommand(firmwareFile.length())
            val startSuccess = bluetoothService.sendOtaControl(startCommand)
            if (!startSuccess) throw IOException("Failed to start OTA process")

            inputStream = FileInputStream(firmwareFile)
            val buffer = ByteArray(512)
            var totalBytesSent = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val dataSuccess = bluetoothService.sendOtaData(
                    buffer.copyOf(bytesRead)
                )
                if (!dataSuccess) throw IOException("Failed to send OTA data packet")

                totalBytesSent += bytesRead
                val progress = (totalBytesSent.toFloat() / firmwareFile.length()) * 100f
                onProgress(progress.coerceIn(0f, 100f))
                delay(30)
            }

            bluetoothService.sendOtaControl(byteArrayOf(OTA_CMD_END))

        } catch (e: Exception) {
            try {
                bluetoothService.sendOtaControl(byteArrayOf(OTA_CMD_ABORT))
            } catch (ignore: Exception) {
            }
            throw IOException("Firmware installation failed: ${e.message}", e)
        } finally {
            inputStream?.close()
            bluetoothService.exitOtaUpdateMode();
        }
    }

    private fun createStartCommand(fileSize: Long): ByteArray {
        val buffer = ByteBuffer.allocate(5)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(OTA_CMD_START)
        buffer.putInt(fileSize.toInt())

        return buffer.array()
    }

    private fun isNewVersionAvailable(current: String, latest: String): Boolean {
        return latest != current
    }
}