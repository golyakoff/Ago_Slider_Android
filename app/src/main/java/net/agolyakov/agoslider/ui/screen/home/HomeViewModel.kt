package net.agolyakov.agoslider.ui.screen.home

import android.Manifest
import android.bluetooth.le.*
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.extensions.toBleDevice
import net.agolyakov.agoslider.data.repository.AppUpdateRepository
import net.agolyakov.agoslider.service.bluetooth.BluetoothAdapterProvider
import net.agolyakov.agoslider.service.bluetooth.BluetoothService
import net.agolyakov.agoslider.domain.repository.PreferencesRepository
import net.agolyakov.agoslider.domain.usecase.LoadDeviceWithNameUseCase
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class HomeViewModel @Inject constructor(
    val bluetoothService: BluetoothService,
    bluetoothAdapterProvider: BluetoothAdapterProvider,
    private val preferencesRepository: PreferencesRepository,
    private val loadDeviceWithNameUseCase: LoadDeviceWithNameUseCase,
    appUpdateRepository: AppUpdateRepository
) : ViewModel() {
    private val foundDevices = HashMap<String, AgoSliderDevice>()
    private val _devices: MutableLiveData<List<AgoSliderDevice>> = MutableLiveData()
    val devices: LiveData<List<AgoSliderDevice>> get() = _devices

    val appVersion: String = appUpdateRepository.currentVersion

    private val _appUpdate = MutableStateFlow<AppUpdateRepository.AppUpdate?>(null)
    val appUpdate: StateFlow<AppUpdateRepository.AppUpdate?> = _appUpdate.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _appUpdate.value = appUpdateRepository.checkForUpdate()
                delay(APP_UPDATE_CHECK_INTERVAL)
            }
        }
    }

    private val adapter = bluetoothAdapterProvider.getAdapter()
    private var scanner: BluetoothLeScanner? = null
    private var callback: BleScanCallback? = null

    private val settings: ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

    private val filters: List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setDeviceName("AGO Slider")
            .build()
    )

    @RequiresPermission(value = Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (callback == null) {
            callback = BleScanCallback()
            scanner = adapter.bluetoothLeScanner
            scanner?.startScan(filters, settings, callback)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (callback != null) {
            scanner?.stopScan(callback)
            scanner = null
            callback = null
        }
    }

    @RequiresPermission(value = Manifest.permission.BLUETOOTH_SCAN)
    override fun onCleared() {
        super.onCleared()
        stopScan()
    }

    inner class BleScanCallback : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val uuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            Log.d("BleScan", "Device: ${device.address}, UUIDs: $uuids")

            if (!foundDevices.containsKey(result.device.address)) {
                val device = result.toBleDevice()
                val enriched = loadDeviceWithNameUseCase(device)
                foundDevices[result.device.address] = enriched
            }
            _devices.postValue(foundDevices.values.toList())
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                if (!foundDevices.containsKey(result.device.address)) {
                    val device = result.toBleDevice()
                    val enriched = loadDeviceWithNameUseCase(device)
                    foundDevices[result.device.address] = enriched
                }
            }
            _devices.postValue(foundDevices.values.toList())
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BluetoothScanner", "onScanFailed: scan error $errorCode")
        }
    }

    fun renameDevice(macAddress: String, newName: String?) {
        if (newName.isNullOrBlank()) {
            preferencesRepository.deleteFriendlyName(macAddress)
        } else {
            preferencesRepository.saveFriendlyName(macAddress, newName)
        }
        foundDevices[macAddress]?.let { device ->
            val updatedDevice = device.copy(friendlyName = if (newName.isNullOrBlank()) null else newName)
            foundDevices[macAddress] = updatedDevice
            _devices.value = foundDevices.values.toList()
        }
    }

    companion object {
        val FILTER_UUID = ParcelUuid.fromString("0000FE95-0000-1000-8000-00805F9B34FB")!!
        private val APP_UPDATE_CHECK_INTERVAL = 10.minutes
    }
}
