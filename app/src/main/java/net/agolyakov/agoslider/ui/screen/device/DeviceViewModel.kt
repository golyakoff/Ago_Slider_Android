package net.agolyakov.agoslider.ui.screen.device

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarmType
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarm
import net.agolyakov.agoslider.ui.component.TimePickerDialogState
import net.agolyakov.agoslider.data.local.AgoSliderPreferences
import net.agolyakov.agoslider.service.bluetooth.BluetoothService
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val preferences: AgoSliderPreferences,
    val bluetoothService: BluetoothService,
    savedStateHandle: SavedStateHandle
): ViewModel() {
    // Device and its FriendlyName
    private val _device = MutableStateFlow<AgoSliderDevice?>(null)
    val device: StateFlow<AgoSliderDevice?> = _device.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _editName = MutableStateFlow("")
    val editName: StateFlow<String> = _editName.asStateFlow()

    fun setDevice(device: AgoSliderDevice) {
//        val deviceWithFriendlyName = preferences.loadFriendlyNameToDevice(device)
//        _device.value = deviceWithFriendlyName
//        _editName.value = deviceWithFriendlyName.friendlyName ?: deviceWithFriendlyName.deviceName
    }

    fun startEditing() {
        _isEditing.value = true
    }

    fun updateEditName(name: String) {
        _editName.value = name
    }

    fun saveFriendlyName() {
        val device = _device.value ?: return
        preferences.saveFriendlyName(device.macAddress, _editName.value)
        _device.value = device.copy(friendlyName = _editName.value)
        _isEditing.value = false
    }

    fun cancelEditing() {
        val device = _device.value ?: return
        _editName.value = device.friendlyName ?: device.deviceName
        _isEditing.value = false
    }

    var agoSliderDeviceTime = bluetoothService.agoSliderDeviceTime
    val agoSliderOn = bluetoothService.agoSliderIsOn
    val agoSliderManualBrightness = bluetoothService.agoSliderManualBrightness
    val agoSliderIsAutoBrightness = bluetoothService.agoSliderIsAutoBrightness
    val agoSliderTurnOnAlarm = bluetoothService.agoSliderTurnOnAlarm
    var agoSliderTurnOffAlarm = bluetoothService.agoSliderTurnOffAlarm
    val agoSliderAgingOffset = bluetoothService.agoSliderAgingOffset
    val agoSliderRtcTemperature  =  bluetoothService.agoSliderRtcTemperature

    private val _manualBrightnessState = MutableStateFlow<Byte>(0)
    private val _debouncedBrightness = MutableSharedFlow<Byte>(extraBufferCapacity = 1)


    // Alarm Dialog
    private val _timePickerState = MutableStateFlow(TimePickerDialogState())
    val timePickerState: StateFlow<TimePickerDialogState> = _timePickerState

    fun showTimePickerDialog(alarmType: AgoSliderAlarmType, alarm: AgoSliderAlarm) {
        _timePickerState.value = TimePickerDialogState(
            isVisible = true,
            alarmType = alarmType,
            hour = alarm.hours.toInt(),
            minute = alarm.minutes.toInt(),
            isActive = alarm.isActive
        )
    }

    fun hideTimePickerDialog() {
        _timePickerState.value = TimePickerDialogState(isVisible = false)
    }


    // Debounce Brightness
    private fun setupDebouncedBrightness() {
        viewModelScope.launch {
            _debouncedBrightness
                .debounce(300) // 300ms debounce
                .collect { value ->
                    bluetoothService.setManualBrightnessCharacteristic(value)
                    _manualBrightnessState.value = value
                }
        }
    }

    // Common

    val connectionState = bluetoothService.connectionState
    val currentDevice = bluetoothService.currentDevice

    init {
        val deviceFromNav = savedStateHandle.get<AgoSliderDevice>("device")
        deviceFromNav?.let {
            setDevice(it)
            bluetoothService.connect(it)
            setupDebouncedBrightness()
        }
    }

    fun connectToDevice(device: AgoSliderDevice?)
    {
        device?.let {
            bluetoothService.connect(it)
            bluetoothService.setShouldMaintainConnection(true)
        }
    }

    fun setAlarmTime(alarmType: AgoSliderAlarmType, hour: Int, minute: Int, isActive: Boolean) {
        Log.d("DeviceViewModel", "setAlarmTime: active=$isActive $hour:$minute for ${alarmType}")
        bluetoothService.setAlarmTime(
            alarmType,
            hour,
            minute,
            isActive)
    }

    fun toggleOnOffCharacteristic() {
        bluetoothService.toggleOnOffCharacteristic()
    }

    fun setManualBrightnessCharacteristic(brightness: Byte) {
        bluetoothService.setManualBrightnessCharacteristic(brightness)
    }

    fun syncBleWithPhone() {
        bluetoothService.syncBleWithPhone()
    }

    fun setAgingOffsetCharacteristic(agingOffset: Int) {
        bluetoothService.setAgingOffsetCharacteristic(agingOffset)
    }

    fun toggleAlarmActive(alarmType: AgoSliderAlarmType) {
        Log.d("DeviceViewModel", "toggleAlarmActive for ${alarmType}")
        bluetoothService.toggleAlarmActive(alarmType)
    }
}
