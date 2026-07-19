package net.agolyakov.agoslider.service.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import net.agolyakov.agoslider.service.bluetooth.handlers.*
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

class AgoSliderManager(
    @ApplicationContext context: Context,
    // Read handlers for characteristics that support read/notify
    private val microstepsHandler: MicrostepsReadCharacteristicHandler,
    private val runCurrentHandler: RunCurrentReadCharacteristicHandler,
    private val holdCurrentHandler: HoldCurrentReadCharacteristicHandler,
    private val axisUnitHandler: AxisUnitReadCharacteristicHandler,
    private val unitsPerStepHandler: UnitsPerStepReadCharacteristicHandler,
    private val axisSpeedHandler: AxisSpeedReadCharacteristicHandler,
    private val axisAccelHandler: AxisAccelReadCharacteristicHandler,
    private val virtualLimitHandler: VirtualLimitReadCharacteristicHandler,
    private val stealthChopHandler: StealthChopReadCharacteristicHandler,
    private val invertDirHandler: InvertDirReadCharacteristicHandler,
    private val batteryLevelHandler: BatteryLevelReadCharacteristicHandler,
    private val powerInfoHandler: PowerInfoReadCharacteristicHandler,
    private val powerInfoStringHandler: PowerInfoStringReadCharacteristicHandler,
    private val limitHandler: LimitReadCharacteristicHandler,
    private val positionHandler: PositionReadCharacteristicHandler,
    private val calibStatusHandler: CalibStatusReadCharacteristicHandler,
    private val homeHandler: HomeReadCharacteristicHandler,
    private val motEnHandler: MotEnReadCharacteristicHandler,
    private val versionHandler: VersionReadCharacteristicHandler,
    // Move is write-only, no handler needed
    // OTA characteristics are write-only
) : BleManager(context) {

    private val tag = "AgoSliderManager"
    private val mtuDeferred = CompletableDeferred<Int>()

    // Characteristic references
    private var motEnCharacteristic: BluetoothGattCharacteristic? = null
    private var homeCharacteristic: BluetoothGattCharacteristic? = null
    private var limitCharacteristic: BluetoothGattCharacteristic? = null
    private var moveCharacteristic: BluetoothGattCharacteristic? = null
    private var positionCharacteristic: BluetoothGattCharacteristic? = null
    private var calibCharacteristic: BluetoothGattCharacteristic? = null

    private var battLevelCharacteristic: BluetoothGattCharacteristic? = null
    private var pwrInfoCharacteristic: BluetoothGattCharacteristic? = null
    private var pwrInfoStrCharacteristic: BluetoothGattCharacteristic? = null

    private var microstepsCharacteristic: BluetoothGattCharacteristic? = null
    private var runCurrentCharacteristic: BluetoothGattCharacteristic? = null
    private var holdCurrentCharacteristic: BluetoothGattCharacteristic? = null
    private var axisUnitCharacteristic: BluetoothGattCharacteristic? = null
    private var unitsPerStepCharacteristic: BluetoothGattCharacteristic? = null
    private var axisSpeedCharacteristic: BluetoothGattCharacteristic? = null
    private var axisAccelCharacteristic: BluetoothGattCharacteristic? = null
    private var virtualLimitCharacteristic: BluetoothGattCharacteristic? = null
    private var stealthChopCharacteristic: BluetoothGattCharacteristic? = null
    private var invertDirCharacteristic: BluetoothGattCharacteristic? = null

    private var versionCharacteristic: BluetoothGattCharacteristic? = null
    private var otaControlCharacteristic: BluetoothGattCharacteristic? = null
    private var otaDataCharacteristic: BluetoothGattCharacteristic? = null

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(SERVICE_CONTROL_UUID) ?: return false

        motEnCharacteristic = service.getCharacteristic(MOT_EN_CHAR_UUID)
        homeCharacteristic = service.getCharacteristic(HOME_CHAR_UUID)
        limitCharacteristic = service.getCharacteristic(LIMIT_CHAR_UUID)
        moveCharacteristic = service.getCharacteristic(MOVE_CHAR_UUID)
        // Optional — present from firmware 0.1.4 on; older devices must keep working,
        // so they are deliberately NOT part of the mandatory check below
        positionCharacteristic = service.getCharacteristic(POSITION_CHAR_UUID)
        calibCharacteristic = service.getCharacteristic(CALIB_CHAR_UUID)

        battLevelCharacteristic = service.getCharacteristic(BATT_LEVEL_CHAR_UUID)
        pwrInfoCharacteristic = service.getCharacteristic(PWR_INFO_CHAR_UUID)
        pwrInfoStrCharacteristic = service.getCharacteristic(PWR_INFO_STR_CHAR_UUID)

        microstepsCharacteristic = service.getCharacteristic(MICROSTEPS_CHAR_UUID)
        runCurrentCharacteristic = service.getCharacteristic(RUN_CURRENT_CHAR_UUID)
        holdCurrentCharacteristic = service.getCharacteristic(HOLD_CURRENT_CHAR_UUID)
        axisUnitCharacteristic = service.getCharacteristic(AXIS_UNIT_CHAR_UUID)
        unitsPerStepCharacteristic = service.getCharacteristic(UNITS_PER_STEP_CHAR_UUID)
        axisSpeedCharacteristic = service.getCharacteristic(AXIS_SPEED_CHAR_UUID)
        axisAccelCharacteristic = service.getCharacteristic(AXIS_ACCEL_CHAR_UUID)
        virtualLimitCharacteristic = service.getCharacteristic(VIRTUAL_LIMIT_CHAR_UUID)
        stealthChopCharacteristic = service.getCharacteristic(STEALTHCHOP_CHAR_UUID)
        invertDirCharacteristic = service.getCharacteristic(INVERT_DIR_CHAR_UUID)

        versionCharacteristic = service.getCharacteristic(VERSION_CHAR_UUID)
        otaControlCharacteristic = service.getCharacteristic(OTA_CONTROL_CHAR_UUID)
        otaDataCharacteristic = service.getCharacteristic(OTA_DATA_CHAR_UUID)

        // All mandatory characteristics must be present
        return motEnCharacteristic != null &&
                homeCharacteristic != null &&
                limitCharacteristic != null &&
                moveCharacteristic != null &&
                battLevelCharacteristic != null &&
                pwrInfoCharacteristic != null &&
                pwrInfoStrCharacteristic != null &&
                microstepsCharacteristic != null &&
                runCurrentCharacteristic != null &&
                holdCurrentCharacteristic != null &&
                axisUnitCharacteristic != null &&
                unitsPerStepCharacteristic != null &&
                axisSpeedCharacteristic != null &&
                axisAccelCharacteristic != null &&
                virtualLimitCharacteristic != null &&
                stealthChopCharacteristic != null &&
                invertDirCharacteristic != null &&
                versionCharacteristic != null &&
                otaControlCharacteristic != null &&
                otaDataCharacteristic != null
    }

    override fun onServicesInvalidated() {
        motEnCharacteristic = null
        homeCharacteristic = null
        limitCharacteristic = null
        moveCharacteristic = null
        positionCharacteristic = null
        calibCharacteristic = null
        battLevelCharacteristic = null
        pwrInfoCharacteristic = null
        pwrInfoStrCharacteristic = null
        microstepsCharacteristic = null
        runCurrentCharacteristic = null
        holdCurrentCharacteristic = null
        axisUnitCharacteristic = null
        unitsPerStepCharacteristic = null
        axisSpeedCharacteristic = null
        axisAccelCharacteristic = null
        virtualLimitCharacteristic = null
        stealthChopCharacteristic = null
        invertDirCharacteristic = null
        versionCharacteristic = null
        otaControlCharacteristic = null
        otaDataCharacteristic = null
    }

    override fun initialize() {
        super.initialize()
        requestMtu(BLE_MTU)
            .with { _, mtu ->
                Log.d(tag, "MTU set to $mtu")
                mtuDeferred.complete(mtu)
            }
            .fail { _, status ->
                Log.w(tag, "MTU request failed, using default 23, status=$status")
                mtuDeferred.complete(23)
            }
            .enqueue()

        // Enable notifications for characteristics that support them
        enableNotification(motEnCharacteristic, motEnHandler)
        enableNotification(homeCharacteristic, homeHandler)
        enableNotification(limitCharacteristic, limitHandler)
        enableNotification(positionCharacteristic, positionHandler)
        enableNotification(calibCharacteristic, calibStatusHandler)
        enableNotification(battLevelCharacteristic, batteryLevelHandler)
        enableNotification(pwrInfoCharacteristic, powerInfoHandler)
        enableNotification(pwrInfoStrCharacteristic, powerInfoStringHandler)
        enableNotification(versionCharacteristic, versionHandler)
    }

    private fun enableNotification(
        characteristic: BluetoothGattCharacteristic?,
        handler: ReadCharacteristicHandler
    ) {
        characteristic?.let {
            setNotificationCallback(it).with { device, data ->
                handler.onReadCharacteristicCallback(device, data)
            }
            enableNotifications(it).enqueue()
        }
    }

    // ---------- Read methods ----------
    fun readMotEnCharacteristic() {
        readCharacteristic(motEnCharacteristic)
            .with { device, data -> motEnHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readPowerInfoCharacteristic() {
        readCharacteristic(pwrInfoCharacteristic)
            .with { device, data -> powerInfoHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readPowerInfoStringCharacteristic() {
        readCharacteristic(pwrInfoStrCharacteristic)
            .with { device, data -> powerInfoStringHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readLimitCharacteristic() {
        readCharacteristic(limitCharacteristic)
            .with { device, data -> limitHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readPositionCharacteristic() {
        val characteristic = positionCharacteristic ?: return // absent on older firmware
        readCharacteristic(characteristic)
            .with { device, data -> positionHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readBattLevelCharacteristic() {
        readCharacteristic(battLevelCharacteristic)
            .with { device, data -> batteryLevelHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readMicrostepsCharacteristic() {
        readCharacteristic(microstepsCharacteristic)
            .with { device, data -> microstepsHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readRunCurrentCharacteristic() {
        readCharacteristic(runCurrentCharacteristic)
            .with { device, data -> runCurrentHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readHoldCurrentCharacteristic() {
        readCharacteristic(holdCurrentCharacteristic)
            .with { device, data -> holdCurrentHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readAxisUnitCharacteristic() {
        readCharacteristic(axisUnitCharacteristic)
            .with { device, data -> axisUnitHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readUnitsPerStepCharacteristic() {
        readCharacteristic(unitsPerStepCharacteristic)
            .with { device, data -> unitsPerStepHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readAxisSpeedCharacteristic() {
        readCharacteristic(axisSpeedCharacteristic)
            .with { device, data -> axisSpeedHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readAxisAccelCharacteristic() {
        readCharacteristic(axisAccelCharacteristic)
            .with { device, data -> axisAccelHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readVirtualLimitCharacteristic() {
        readCharacteristic(virtualLimitCharacteristic)
            .with { device, data -> virtualLimitHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readStealthChopCharacteristic() {
        readCharacteristic(stealthChopCharacteristic)
            .with { device, data -> stealthChopHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readInvertDirCharacteristic() {
        readCharacteristic(invertDirCharacteristic)
            .with { device, data -> invertDirHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    fun readVersionCharacteristic() {
        readCharacteristic(versionCharacteristic)
            .with { device, data -> versionHandler.onReadCharacteristicCallback(device, data) }
            .enqueue()
    }

    // ---------- Write methods (no response expected for most) ----------
    fun writeMotEnCharacteristic(enabled: Boolean) {
        val data = byteArrayOf(if (enabled) 1 else 0)
        writeCharacteristic(motEnCharacteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeHomeCommand(homeX: Boolean, homeC: Boolean, homeB: Boolean) {
        var cmd = 0
        if (homeX) cmd = cmd or 0x10
        if (homeC) cmd = cmd or 0x20
        if (homeB) cmd = cmd or 0x40
        writeCharacteristic(homeCharacteristic, byteArrayOf(cmd.toByte()), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    /**
     * Start hardware calibration of [axis] (0=X, 1=C, 2=B). Returns false when the
     * firmware has no CALIBRATE characteristic (pre-0.1.4).
     */
    fun writeCalibrateCommand(axis: Int, parkOffsetSteps: Int, retreatSteps: Int): Boolean {
        val characteristic = calibCharacteristic ?: return false
        val buffer = java.nio.ByteBuffer.allocate(9).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put(axis.toByte())
        buffer.putInt(parkOffsetSteps)
        buffer.putInt(retreatSteps)
        writeCharacteristic(characteristic, buffer.array(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
        return true
    }

    /** Abort a running hardware calibration (force-stops the axis on the device). */
    fun writeCalibrateAbort(): Boolean {
        val characteristic = calibCharacteristic ?: return false
        writeCharacteristic(characteristic, byteArrayOf(0xFF.toByte()), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
        return true
    }

    fun writeMoveCommand(x: Int, c: Int, b: Int) {
        val buffer = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(x)
        buffer.putInt(c)
        buffer.putInt(b)
        writeCharacteristic(moveCharacteristic, buffer.array(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeMicrostepsCharacteristic(x: Byte, c: Byte, b: Byte) {
        writeCharacteristic(microstepsCharacteristic, byteArrayOf(x, c, b), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeRunCurrentCharacteristic(x: Int, c: Int, b: Int) {
        val buffer = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(x.toShort())
        buffer.putShort(c.toShort())
        buffer.putShort(b.toShort())
        writeCharacteristic(runCurrentCharacteristic, buffer.array(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeHoldCurrentCharacteristic(x: Int, c: Int, b: Int) {
        val buffer = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(x.toShort())
        buffer.putShort(c.toShort())
        buffer.putShort(b.toShort())
        writeCharacteristic(holdCurrentCharacteristic, buffer.array(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeAxisUnitCharacteristic(xDeg: Boolean, cDeg: Boolean, bDeg: Boolean) {
        var flags = 0
        if (xDeg) flags = flags or 0x01
        if (cDeg) flags = flags or 0x02
        if (bDeg) flags = flags or 0x04
        writeCharacteristic(axisUnitCharacteristic, byteArrayOf(flags.toByte()), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeUnitsPerStepCharacteristic(x: Float, c: Float, b: Float) {
        val buffer = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(x)
        buffer.putFloat(c)
        buffer.putFloat(b)
        writeCharacteristic(unitsPerStepCharacteristic, buffer.array(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeAxisSpeedCharacteristic(x: Int, c: Int, b: Int) {
        val buffer = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(x.toShort())
        buffer.putShort(c.toShort())
        buffer.putShort(b.toShort())
        writeCharacteristic(axisSpeedCharacteristic, buffer.array(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeAxisAccelCharacteristic(x: Int, c: Int, b: Int) {
        val buffer = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(x.toShort())
        buffer.putShort(c.toShort())
        buffer.putShort(b.toShort())
        writeCharacteristic(axisAccelCharacteristic, buffer.array(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeVirtualLimitCharacteristic(xEn: Boolean, cEn: Boolean, bEn: Boolean) {
        var flags = 0
        if (xEn) flags = flags or 0x01
        if (cEn) flags = flags or 0x02
        if (bEn) flags = flags or 0x04
        writeCharacteristic(virtualLimitCharacteristic, byteArrayOf(flags.toByte()), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeStealthChopCharacteristic(xEn: Boolean, cEn: Boolean, bEn: Boolean) {
        var flags = 0
        if (xEn) flags = flags or 0x01
        if (cEn) flags = flags or 0x02
        if (bEn) flags = flags or 0x04
        writeCharacteristic(stealthChopCharacteristic, byteArrayOf(flags.toByte()), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    fun writeInvertDirCharacteristic(xInv: Boolean, cInv: Boolean, bInv: Boolean) {
        var flags = 0
        if (xInv) flags = flags or 0x01
        if (cInv) flags = flags or 0x02
        if (bInv) flags = flags or 0x04
        writeCharacteristic(invertDirCharacteristic, byteArrayOf(flags.toByte()), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
    }

    /**
     * Negotiated ATT MTU (suspends until the MTU exchange completes after connect).
     * OTA chunks must not exceed MTU-3 or Android falls back to slow long writes.
     */
    suspend fun awaitNegotiatedMtu(): Int = mtuDeferred.await()

    // OTA methods with callback
    fun writeOtaControlCharacteristic(command: ByteArray, callback: (Boolean) -> Unit) {
        writeCharacteristic(otaControlCharacteristic, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            .with { _, _ -> callback(true) }
            .fail { _, _ -> callback(false) }
            .enqueue()
    }

    fun writeOtaDataCharacteristic(data: ByteArray, callback: (Boolean) -> Unit) {
        writeCharacteristic(otaDataCharacteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            .with { _, _ -> callback(true) }
            .fail { _, _ -> callback(false) }
            .enqueue()
    }

    companion object {
        // Service UUID
        val SERVICE_CONTROL_UUID: UUID = UUID.fromString("0000FE95-0000-1000-8000-00805F9B34FB")

        // Characteristic UUIDs (16-bit → 128-bit)
        val MOT_EN_CHAR_UUID: UUID       = UUID.fromString("0000F001-0000-1000-8000-00805F9B34FB")
        val HOME_CHAR_UUID: UUID         = UUID.fromString("0000F002-0000-1000-8000-00805F9B34FB")
        val LIMIT_CHAR_UUID: UUID        = UUID.fromString("0000F003-0000-1000-8000-00805F9B34FB")
        val MOVE_CHAR_UUID: UUID         = UUID.fromString("0000F004-0000-1000-8000-00805F9B34FB")
        val POSITION_CHAR_UUID: UUID     = UUID.fromString("0000F005-0000-1000-8000-00805F9B34FB")
        val CALIB_CHAR_UUID: UUID        = UUID.fromString("0000F006-0000-1000-8000-00805F9B34FB")

        val BATT_LEVEL_CHAR_UUID: UUID   = UUID.fromString("0000F020-0000-1000-8000-00805F9B34FB")
        val PWR_INFO_CHAR_UUID: UUID     = UUID.fromString("0000F021-0000-1000-8000-00805F9B34FB")
        val PWR_INFO_STR_CHAR_UUID: UUID = UUID.fromString("0000F022-0000-1000-8000-00805F9B34FB")

        val MICROSTEPS_CHAR_UUID: UUID    = UUID.fromString("0000F030-0000-1000-8000-00805F9B34FB")
        val RUN_CURRENT_CHAR_UUID: UUID   = UUID.fromString("0000F031-0000-1000-8000-00805F9B34FB")
        val HOLD_CURRENT_CHAR_UUID: UUID  = UUID.fromString("0000F032-0000-1000-8000-00805F9B34FB")
        val AXIS_UNIT_CHAR_UUID: UUID     = UUID.fromString("0000F033-0000-1000-8000-00805F9B34FB")
        val UNITS_PER_STEP_CHAR_UUID: UUID = UUID.fromString("0000F034-0000-1000-8000-00805F9B34FB")
        val AXIS_SPEED_CHAR_UUID: UUID    = UUID.fromString("0000F035-0000-1000-8000-00805F9B34FB")
        val AXIS_ACCEL_CHAR_UUID: UUID    = UUID.fromString("0000F036-0000-1000-8000-00805F9B34FB")
        val VIRTUAL_LIMIT_CHAR_UUID: UUID = UUID.fromString("0000F037-0000-1000-8000-00805F9B34FB")
        val STEALTHCHOP_CHAR_UUID: UUID   = UUID.fromString("0000F038-0000-1000-8000-00805F9B34FB")
        val INVERT_DIR_CHAR_UUID: UUID    = UUID.fromString("0000F039-0000-1000-8000-00805F9B34FB")

        val VERSION_CHAR_UUID: UUID       = UUID.fromString("0000F090-0000-1000-8000-00805F9B34FB")
        val OTA_CONTROL_CHAR_UUID: UUID   = UUID.fromString("0000F091-0000-1000-8000-00805F9B34FB")
        val OTA_DATA_CHAR_UUID: UUID      = UUID.fromString("0000F092-0000-1000-8000-00805F9B34FB")

        // OTA commands
        const val OTA_CMD_START: Byte = 0x01
        const val OTA_CMD_END: Byte   = 0x02
        const val OTA_CMD_ABORT: Byte = 0x03

        // MTU size (max 517 for full support)
        const val BLE_MTU: Int = 517
    }
}