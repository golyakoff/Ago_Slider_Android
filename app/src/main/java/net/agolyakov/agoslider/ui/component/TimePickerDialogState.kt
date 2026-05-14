package net.agolyakov.agoslider.ui.component

import net.agolyakov.agoslider.data.model.ble.AgoSliderAlarmType

data class TimePickerDialogState(
    val isVisible: Boolean = false,
    val alarmType: AgoSliderAlarmType = AgoSliderAlarmType.TURN_ON,
    val hour: Int = 0,
    val minute: Int = 0,
    val isActive: Boolean = false
)