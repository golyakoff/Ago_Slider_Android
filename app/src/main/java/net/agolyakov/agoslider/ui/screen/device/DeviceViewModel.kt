package net.agolyakov.agoslider.ui.screen.device

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.data.model.position.PositioningSettings
import net.agolyakov.agoslider.service.bluetooth.BluetoothService
import net.agolyakov.agoslider.service.position.PositionManager
import net.agolyakov.agoslider.service.scenario.FocusScenarioManager
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val positionManager: PositionManager,
    private val focusScenarioManager: FocusScenarioManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Connection state
    val connectionState = bluetoothService.connectionState
    //val currentDevice = bluetoothService.currentDevice

    // Axis configuration StateFlows from service
    val motorsEnabled = bluetoothService.motorsEnabled
    val microsteps = bluetoothService.microsteps
    val runCurrent = bluetoothService.runCurrent
    val holdCurrent = bluetoothService.holdCurrent
    val axisUnit = bluetoothService.axisUnit
    val unitsPerStep = bluetoothService.unitsPerStep
    val axisSpeed = bluetoothService.axisSpeed
    val axisAccel = bluetoothService.axisAccel
    val virtualLimit = bluetoothService.virtualLimit
    val stealthChop = bluetoothService.stealthChop
    val invertDir = bluetoothService.invertDir

    // Status values
    val limitStatus = bluetoothService.limitStatus
    val homeStatus = bluetoothService.homeStatus
    val powerInfo = bluetoothService.powerInfo
    val powerInfoString = bluetoothService.powerInfoString
    val powerHistory = bluetoothService.powerHistory

    // The scenario runs on the device, so both of these are its report rather than our record
    val scenarioState = focusScenarioManager.state
    val scenarioStatus = focusScenarioManager.status

    fun jogScenarioC(deltaDeg: Float) = focusScenarioManager.jogC(deltaDeg)
    fun jogScenarioX(deltaMm: Float) = focusScenarioManager.jogX(deltaMm)
    fun markScenarioAim(xTravel: Float) = focusScenarioManager.markAim(xTravel)
    fun clearScenarioAims() = focusScenarioManager.clearAims()
    fun startScenario(xTravel: Float, bTravel: Float, seconds: Float, distanceMm: Float?) {
        focusScenarioManager.start(xTravel, bTravel, seconds, distanceMm)
    }
    fun stopScenario() = focusScenarioManager.stop()
    val batteryLevel = bluetoothService.batteryLevel
    val firmwareVersion = bluetoothService.firmwareVersion

    // Virtual coordinates (see PositionManager for the validity rules)
    val coordinates = positionManager.coordinates
    val positioning = positionManager.settings
    val calibration = positionManager.calibration

    // Move command, in the axis's own unit (mm or degrees) — the conversion to steps and the
    // soft-limit clamping happen in PositionManager
    private val _moveX = MutableStateFlow(0)
    val moveX: StateFlow<Int> = _moveX.asStateFlow()
    private val _moveC = MutableStateFlow(0)
    val moveC: StateFlow<Int> = _moveC.asStateFlow()
    private val _moveB = MutableStateFlow(0)
    val moveB: StateFlow<Int> = _moveB.asStateFlow()

    fun updateMoveX(value: Int) { _moveX.value = value }
    fun updateMoveC(value: Int) { _moveC.value = value }
    fun updateMoveB(value: Int) { _moveB.value = value }

    fun sendMoveCommand() {
        positionManager.moveRelative(
            _moveX.value.toFloat(),
            _moveC.value.toFloat(),
            _moveB.value.toFloat()
        )
    }

    fun setMotorsEnabled(enabled: Boolean) {
        bluetoothService.setMotorsEnabled(enabled)
    }

    fun sendHomeCommand(homeX: Boolean, homeC: Boolean, homeB: Boolean) {
        positionManager.startHoming(homeX, homeC, homeB)
    }

    fun savePositioning(settings: PositioningSettings) {
        positionManager.saveSettings(settings)
    }

    fun startCalibration(axis: Int) {
        positionManager.startCalibration(axis)
    }

    fun cancelCalibration() {
        positionManager.cancelCalibration()
    }

    fun setMicrosteps(x: Int, c: Int, b: Int) {
        bluetoothService.setMicrosteps(x, c, b)
    }

    fun setRunCurrent(x: Int, c: Int, b: Int) {
        bluetoothService.setRunCurrent(x, c, b)
    }

    fun setHoldCurrent(x: Int, c: Int, b: Int) {
        bluetoothService.setHoldCurrent(x, c, b)
    }

    fun setAxisUnit(xDeg: Boolean, cDeg: Boolean, bDeg: Boolean) {
        bluetoothService.setAxisUnit(xDeg, cDeg, bDeg)
    }

    fun setUnitsPerStep(x: Float, c: Float, b: Float) {
        bluetoothService.setUnitsPerStep(x, c, b)
    }

    fun setAxisSpeed(x: Int, c: Int, b: Int) {
        bluetoothService.setAxisSpeed(x, c, b)
    }

    fun setAxisAccel(x: Int, c: Int, b: Int) {
        bluetoothService.setAxisAccel(x, c, b)
    }

    fun setVirtualLimit(xEnable: Boolean, cEnable: Boolean, bEnable: Boolean) {
        bluetoothService.setVirtualLimit(xEnable, cEnable, bEnable)
    }

    fun setStealthChop(xEnable: Boolean, cEnable: Boolean, bEnable: Boolean) {
        bluetoothService.setStealthChop(xEnable, cEnable, bEnable)
    }

    fun setInvertDir(xInvert: Boolean, cInvert: Boolean, bInvert: Boolean) {
        bluetoothService.setInvertDir(xInvert, cInvert, bInvert)
    }

    fun connectToDevice(device: AgoSliderDevice?) {
        device?.let {
            bluetoothService.connect(it)
            bluetoothService.setShouldMaintainConnection(true)
        }
    }
}