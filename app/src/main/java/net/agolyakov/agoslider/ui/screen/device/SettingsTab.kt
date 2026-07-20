package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
import net.agolyakov.agoslider.data.model.position.PositioningSettings
import net.agolyakov.agoslider.ui.theme.AgoSliderTheme

// ----------------------------------------------------------------------------
// Settings tab: axis/driver configuration persisted on the device
// (microsteps, currents, units, speed/accel, limits, driver flags)
// ----------------------------------------------------------------------------
@Composable
fun SettingsTabContent(
    microsteps: Triple<Int, Int, Int>,
    runCurrent: Triple<Int, Int, Int>,
    holdCurrent: Triple<Int, Int, Int>,
    axisUnit: Triple<Boolean, Boolean, Boolean>,
    unitsPerStep: Triple<Float, Float, Float>,
    axisSpeed: Triple<Int, Int, Int>,
    axisAccel: Triple<Int, Int, Int>,
    virtualLimit: Triple<Boolean, Boolean, Boolean>,
    continuous: Triple<Boolean, Boolean, Boolean>,
    stealthChop: Triple<Boolean, Boolean, Boolean>,
    invertDir: Triple<Boolean, Boolean, Boolean>,
    positioning: PositioningSettings,
    onMicrostepsChange: (Int, Int, Int) -> Unit,
    onRunCurrentChange: (Int, Int, Int) -> Unit,
    onHoldCurrentChange: (Int, Int, Int) -> Unit,
    onAxisUnitChange: (Boolean, Boolean, Boolean) -> Unit,
    onUnitsPerStepChange: (Float, Float, Float) -> Unit,
    onAxisSpeedChange: (Int, Int, Int) -> Unit,
    onAxisAccelChange: (Int, Int, Int) -> Unit,
    onVirtualLimitChange: (Boolean, Boolean, Boolean) -> Unit,
    onContinuousChange: (Boolean, Boolean, Boolean) -> Unit,
    onStealthChopChange: (Boolean, Boolean, Boolean) -> Unit,
    onInvertDirChange: (Boolean, Boolean, Boolean) -> Unit,
    onSavePositioning: (PositioningSettings) -> Unit
) {
    // The edited copy of every card, reset whenever the device reports new values. Kept here
    // rather than inside the cards because the speed card is derived from three of them.
    var runCurrentEdit by remember(runCurrent) { mutableStateOf(runCurrent) }
    var holdCurrentEdit by remember(holdCurrent) { mutableStateOf(holdCurrent) }
    var microstepsEdit by remember(microsteps) { mutableStateOf(microsteps) }
    var invertDirEdit by remember(invertDir) { mutableStateOf(invertDir) }
    var axisUnitEdit by remember(axisUnit) { mutableStateOf(axisUnit) }
    var axisAccelEdit by remember(axisAccel) { mutableStateOf(axisAccel) }
    var axisSpeedEdit by remember(axisSpeed) { mutableStateOf(axisSpeed) }
    var virtualLimitEdit by remember(virtualLimit) { mutableStateOf(virtualLimit) }
    var continuousEdit by remember(continuous) { mutableStateOf(continuous) }
    var stealthChopEdit by remember(stealthChop) { mutableStateOf(stealthChop) }
    // Null while a field is half-typed and cannot be parsed
    var unitsPerStepEdit by remember(unitsPerStep) {
        mutableStateOf<Triple<Float, Float, Float>?>(unitsPerStep)
    }

    // Which group is open. Only one at a time: there are enough cards that leaving several
    // expanded would bring back the endless scroll the grouping exists to remove.
    var openGroup by remember { mutableStateOf(SettingsGroupId.ELECTRICAL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(
            icon = Icons.Default.Bolt,
            title = stringResource(R.string.settings_group_electrical),
            expanded = openGroup == SettingsGroupId.ELECTRICAL,
            onToggle = { openGroup = openGroup.toggledWith(SettingsGroupId.ELECTRICAL) }
        ) {
            IntSliderTriple(
                icon = Icons.Default.PlayCircle,
                title = stringResource(R.string.settings_run_current),
                values = runCurrentEdit,
                range = CURRENT_RANGE,
                step = CURRENT_STEP,
                dirty = runCurrentEdit != runCurrent,
                onValueChange = { runCurrentEdit = it },
                onSave = { onRunCurrentChange(runCurrentEdit.first, runCurrentEdit.second, runCurrentEdit.third) }
            )
            IntSliderTriple(
                icon = Icons.Default.PauseCircle,
                title = stringResource(R.string.settings_hold_current),
                values = holdCurrentEdit,
                range = CURRENT_RANGE,
                step = CURRENT_STEP,
                dirty = holdCurrentEdit != holdCurrent,
                onValueChange = { holdCurrentEdit = it },
                onSave = { onHoldCurrentChange(holdCurrentEdit.first, holdCurrentEdit.second, holdCurrentEdit.third) }
            )
            BoolTriple(
                icon = Icons.Default.VolumeOff,
                title = stringResource(R.string.settings_stealthchop),
                values = stealthChopEdit,
                dirty = stealthChopEdit != stealthChop,
                onValueChange = { stealthChopEdit = it },
                onSave = { onStealthChopChange(stealthChopEdit.first, stealthChopEdit.second, stealthChopEdit.third) }
            )
        }

        SettingsGroup(
            icon = Icons.Default.Speed,
            title = stringResource(R.string.settings_group_motion),
            expanded = openGroup == SettingsGroupId.MOTION,
            onToggle = { openGroup = openGroup.toggledWith(SettingsGroupId.MOTION) }
        ) {
            IntDropdownTriple(
                icon = Icons.Default.LinearScale,
                title = stringResource(R.string.settings_microsteps),
                values = microstepsEdit,
                options = MICROSTEP_OPTIONS,
                dirty = microstepsEdit != microsteps,
                onValueChange = { microstepsEdit = it },
                onSave = { onMicrostepsChange(microstepsEdit.first, microstepsEdit.second, microstepsEdit.third) }
            )
            IntSliderTriple(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = stringResource(R.string.settings_axis_accel),
                values = axisAccelEdit,
                range = ACCEL_RANGE,
                step = ACCEL_STEP,
                dirty = axisAccelEdit != axisAccel,
                onValueChange = { axisAccelEdit = it },
                onSave = { onAxisAccelChange(axisAccelEdit.first, axisAccelEdit.second, axisAccelEdit.third) }
            )
            AxisSpeedTriple(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.settings_axis_speed),
                values = axisSpeedEdit,
                range = SPEED_RANGE,
                step = SPEED_STEP,
                // Read the edited copies, so the resulting speed follows a pending change to any
                // of the values it is derived from, before that change is saved
                microsteps = microstepsEdit,
                unitsPerStep = unitsPerStepEdit ?: unitsPerStep,
                axisIsDegrees = axisUnitEdit,
                dirty = axisSpeedEdit != axisSpeed,
                onValueChange = { axisSpeedEdit = it },
                onSave = { onAxisSpeedChange(axisSpeedEdit.first, axisSpeedEdit.second, axisSpeedEdit.third) }
            )
            BoolTriple(
                icon = Icons.Default.SwapHoriz,
                title = stringResource(R.string.settings_invert_dir),
                values = invertDirEdit,
                dirty = invertDirEdit != invertDir,
                onValueChange = { invertDirEdit = it },
                onSave = { onInvertDirChange(invertDirEdit.first, invertDirEdit.second, invertDirEdit.third) }
            )
            BoolTriple(
                icon = Icons.Default.PanTool,
                title = stringResource(R.string.settings_virtual_limit),
                values = virtualLimitEdit,
                dirty = virtualLimitEdit != virtualLimit,
                onValueChange = { virtualLimitEdit = it },
                onSave = { onVirtualLimitChange(virtualLimitEdit.first, virtualLimitEdit.second, virtualLimitEdit.third) }
            )
            // Tells calibration that the axis has no ends: it turns full circles past one
            // index magnet, so its zero is that magnet and its range is a whole revolution
            BoolTriple(
                icon = Icons.Default.Autorenew,
                title = stringResource(R.string.settings_continuous),
                values = continuousEdit,
                dirty = continuousEdit != continuous,
                onValueChange = { continuousEdit = it },
                onSave = { onContinuousChange(continuousEdit.first, continuousEdit.second, continuousEdit.third) }
            )
        }

        SettingsGroup(
            icon = Icons.Default.Straighten,
            title = stringResource(R.string.settings_group_constants),
            expanded = openGroup == SettingsGroupId.CONSTANTS,
            onToggle = { openGroup = openGroup.toggledWith(SettingsGroupId.CONSTANTS) }
        ) {
            AxisUnitTriple(
                icon = Icons.Default.Straighten,
                title = stringResource(R.string.settings_axis_unit),
                values = axisUnitEdit,
                dirty = axisUnitEdit != axisUnit,
                onValueChange = { axisUnitEdit = it },
                onSave = { onAxisUnitChange(axisUnitEdit.first, axisUnitEdit.second, axisUnitEdit.third) }
            )
            FloatTriple(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.settings_units_per_step),
                deviceValues = unitsPerStep,
                // The edited copy, so the "/step" captions follow the unit radio buttons live
                axisIsDegrees = axisUnitEdit,
                dirty = unitsPerStepEdit != null && unitsPerStepEdit != unitsPerStep,
                onValueChange = { unitsPerStepEdit = it },
                onSave = {
                    unitsPerStepEdit?.let { onUnitsPerStepChange(it.first, it.second, it.third) }
                }
            )
            // Virtual coordinate settings — stored on the phone per device, not on the slider
            PositioningCard(
                icon = Icons.Default.MyLocation,
                title = stringResource(R.string.settings_positioning),
                storedValues = positioning,
                axisIsDegrees = axisUnitEdit,
                onSave = onSavePositioning
            )
        }
    }
}

private enum class SettingsGroupId { NONE, ELECTRICAL, MOTION, CONSTANTS }

/** Tapping the open group's header closes it; tapping another one switches to it. */
private fun SettingsGroupId.toggledWith(tapped: SettingsGroupId): SettingsGroupId =
    if (this == tapped) SettingsGroupId.NONE else tapped

private val MICROSTEP_OPTIONS = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256)
private val CURRENT_RANGE = 200..1200
private const val CURRENT_STEP = 100
private val ACCEL_RANGE = 1000..5000
private const val ACCEL_STEP = 100
// Firmware 0.1.5 made the setting mean what it says (it used to run at a tenth of it), so
// the old 1000..40000 scale now spans speeds the mechanics cannot follow.
private val SPEED_RANGE = 500..10000
private const val SPEED_STEP = 500

// ----------------------------------------------------------------------------
// Previews
// ----------------------------------------------------------------------------
@Composable
private fun SettingsTabPreview(darkTheme: Boolean) {
    AgoSliderTheme(darkTheme) {
        SettingsTabContent(
            microsteps = Triple(16, 16, 16),
            runCurrent = Triple(900, 700, 900),
            holdCurrent = Triple(450, 350, 450),
            axisUnit = Triple(false, true, true),
            unitsPerStep = Triple(0.19195f, 0.2125f, 0.29268f),
            axisSpeed = Triple(1000, 1000, 1000),
            axisAccel = Triple(1000, 1000, 1000),
            virtualLimit = Triple(true, false, true),
            continuous = Triple(false, true, false),
            stealthChop = Triple(true, true, true),
            invertDir = Triple(false, false, false),
            positioning = PositioningSettings.DEFAULT,
            onMicrostepsChange = { _, _, _ -> },
            onRunCurrentChange = { _, _, _ -> },
            onHoldCurrentChange = { _, _, _ -> },
            onAxisUnitChange = { _, _, _ -> },
            onUnitsPerStepChange = { _, _, _ -> },
            onAxisSpeedChange = { _, _, _ -> },
            onAxisAccelChange = { _, _, _ -> },
            onVirtualLimitChange = { _, _, _ -> },
            onContinuousChange = { _, _, _ -> },
            onStealthChopChange = { _, _, _ -> },
            onInvertDirChange = { _, _, _ -> },
            onSavePositioning = {}
        )
    }
}

@Preview(name = "Light Theme", showBackground = true, heightDp = 1600)
@Composable
private fun SettingsTabLightPreview() {
    SettingsTabPreview(darkTheme = false)
}

@Preview(name = "Dark Theme", showBackground = true, heightDp = 1600)
@Composable
private fun SettingsTabDarkPreview() {
    SettingsTabPreview(darkTheme = true)
}
