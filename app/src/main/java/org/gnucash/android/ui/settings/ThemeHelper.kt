package org.gnucash.android.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import org.gnucash.android.ui.settings.ThemePreferences.Values.THEME_DARK
import org.gnucash.android.ui.settings.ThemePreferences.Values.THEME_LIGHT

class ThemeHelper(private val context: Context) {

    private val preferences: ThemePreferences by lazy { SimpleThemePreferences(context) }

    fun apply() {
        when (preferences.themeValue) {
            THEME_DARK -> setDefaultNightMode(MODE_NIGHT_YES)
            THEME_LIGHT -> setDefaultNightMode(MODE_NIGHT_NO)
            else -> setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    companion object {
        @JvmStatic
        fun apply(context: Context) {
            ThemeHelper(context).apply()
        }
    }
}