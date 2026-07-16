package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.agoslider.R
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
    stealthChop: Triple<Boolean, Boolean, Boolean>,
    invertDir: Triple<Boolean, Boolean, Boolean>,
    onMicrostepsChange: (Int, Int, Int) -> Unit,
    onRunCurrentChange: (Int, Int, Int) -> Unit,
    onHoldCurrentChange: (Int, Int, Int) -> Unit,
    onAxisUnitChange: (Boolean, Boolean, Boolean) -> Unit,
    onUnitsPerStepChange: (Float, Float, Float) -> Unit,
    onAxisSpeedChange: (Int, Int, Int) -> Unit,
    onAxisAccelChange: (Int, Int, Int) -> Unit,
    onVirtualLimitChange: (Boolean, Boolean, Boolean) -> Unit,
    onStealthChopChange: (Boolean, Boolean, Boolean) -> Unit,
    onInvertDirChange: (Boolean, Boolean, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IntSliderTriple(
            title = stringResource(R.string.settings_run_current),
            values = runCurrent,
            range = CURRENT_RANGE,
            step = CURRENT_STEP,
            onValueChange = onRunCurrentChange
        )
        IntSliderTriple(
            title = stringResource(R.string.settings_hold_current),
            values = holdCurrent,
            range = CURRENT_RANGE,
            step = CURRENT_STEP,
            onValueChange = onHoldCurrentChange
        )
        IntDropdownTriple(
            title = stringResource(R.string.settings_microsteps),
            values = microsteps,
            options = MICROSTEP_OPTIONS,
            onValueChange = onMicrostepsChange
        )
        BoolTriple(stringResource(R.string.settings_invert_dir), invertDir, onInvertDirChange)
        AxisUnitTriple(stringResource(R.string.settings_axis_unit), axisUnit, onAxisUnitChange)
        FloatTriple(stringResource(R.string.settings_units_per_step), unitsPerStep, onUnitsPerStepChange)
        IntSliderTriple(
            title = stringResource(R.string.settings_axis_accel),
            values = axisAccel,
            range = ACCEL_RANGE,
            step = ACCEL_STEP,
            onValueChange = onAxisAccelChange
        )
        ConfigTriple(stringResource(R.string.settings_axis_speed), axisSpeed, onAxisSpeedChange)
        BoolTriple(stringResource(R.string.settings_virtual_limit), virtualLimit, onVirtualLimitChange)
        BoolTriple(stringResource(R.string.settings_stealthchop), stealthChop, onStealthChopChange)
    }
}

private val MICROSTEP_OPTIONS = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256)
private val CURRENT_RANGE = 200..1200
private const val CURRENT_STEP = 100
private val ACCEL_RANGE = 100..3000
private const val ACCEL_STEP = 100

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
            stealthChop = Triple(true, true, true),
            invertDir = Triple(false, false, false),
            onMicrostepsChange = { _, _, _ -> },
            onRunCurrentChange = { _, _, _ -> },
            onHoldCurrentChange = { _, _, _ -> },
            onAxisUnitChange = { _, _, _ -> },
            onUnitsPerStepChange = { _, _, _ -> },
            onAxisSpeedChange = { _, _, _ -> },
            onAxisAccelChange = { _, _, _ -> },
            onVirtualLimitChange = { _, _, _ -> },
            onStealthChopChange = { _, _, _ -> },
            onInvertDirChange = { _, _, _ -> }
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
