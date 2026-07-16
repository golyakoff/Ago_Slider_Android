package net.agolyakov.agoslider.data.local

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

enum class AppLanguage(val tag: String) {
    English("en"),
    Russian("ru")
}

/**
 * The in-app language choice. Kept apart from [AgoSliderPreferences], whose file holds one
 * entry per device MAC address and is read back as a whole.
 *
 * Read from `MainActivity.attachBaseContext`, i.e. before Hilt can inject anything, so this
 * takes a plain [Context] rather than being injected there.
 */
class LanguagePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() {
            val tag = prefs.getString(KEY_LANGUAGE, null)
            return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.English
        }
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.tag) }

    private companion object {
        const val KEY_LANGUAGE = "language"
    }
}

/**
 * A context whose resources resolve in [language], regardless of the system locale.
 *
 * Also sets the JVM default locale, so that number and date formatting done outside of any
 * context (`String.format` and friends) follows the same choice.
 */
fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.tag)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}
