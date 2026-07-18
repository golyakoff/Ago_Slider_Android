package net.agolyakov.agoslider.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import net.agolyakov.agoslider.data.model.position.PositioningSettings
import javax.inject.Inject

/**
 * Per-device (keyed by MAC address) virtual coordinate settings: home offsets and soft limits.
 * Its own SharedPreferences file — AgoSliderPreferences is read back as a whole map of
 * MAC-to-friendly-name entries, so these keys must not live there.
 */
class PositioningPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "positioning_settings",
        Context.MODE_PRIVATE
    )

    fun get(macAddress: String): PositioningSettings {
        val d = PositioningSettings.DEFAULT
        fun read(key: String, default: Float) = prefs.getFloat("$macAddress:$key", default)
        return PositioningSettings(
            homeOffset = Triple(
                read(KEY_OFFSET_X, d.homeOffset.first),
                read(KEY_OFFSET_C, d.homeOffset.second),
                read(KEY_OFFSET_B, d.homeOffset.third)
            ),
            limitMin = Triple(
                read(KEY_MIN_X, d.limitMin.first),
                read(KEY_MIN_C, d.limitMin.second),
                read(KEY_MIN_B, d.limitMin.third)
            ),
            limitMax = Triple(
                read(KEY_MAX_X, d.limitMax.first),
                read(KEY_MAX_C, d.limitMax.second),
                read(KEY_MAX_B, d.limitMax.third)
            )
        )
    }

    fun save(macAddress: String, settings: PositioningSettings) {
        prefs.edit {
            putFloat("$macAddress:$KEY_OFFSET_X", settings.homeOffset.first)
            putFloat("$macAddress:$KEY_OFFSET_C", settings.homeOffset.second)
            putFloat("$macAddress:$KEY_OFFSET_B", settings.homeOffset.third)
            putFloat("$macAddress:$KEY_MIN_X", settings.limitMin.first)
            putFloat("$macAddress:$KEY_MIN_C", settings.limitMin.second)
            putFloat("$macAddress:$KEY_MIN_B", settings.limitMin.third)
            putFloat("$macAddress:$KEY_MAX_X", settings.limitMax.first)
            putFloat("$macAddress:$KEY_MAX_C", settings.limitMax.second)
            putFloat("$macAddress:$KEY_MAX_B", settings.limitMax.third)
        }
    }

    private companion object {
        const val KEY_OFFSET_X = "home_offset_x"
        const val KEY_OFFSET_C = "home_offset_c"
        const val KEY_OFFSET_B = "home_offset_b"
        const val KEY_MIN_X = "limit_min_x"
        const val KEY_MIN_C = "limit_min_c"
        const val KEY_MIN_B = "limit_min_b"
        const val KEY_MAX_X = "limit_max_x"
        const val KEY_MAX_C = "limit_max_c"
        const val KEY_MAX_B = "limit_max_b"
    }
}
