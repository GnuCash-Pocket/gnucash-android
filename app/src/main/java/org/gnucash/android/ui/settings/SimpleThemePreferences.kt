/*
 * Copyright 2012, Moshe Waisberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.gnucash.android.R
import org.gnucash.android.app.isNightMode
import org.gnucash.android.ui.settings.ThemePreferences.Values.THEME_DARK
import org.gnucash.android.ui.settings.ThemePreferences.Values.THEME_DEFAULT
import org.gnucash.android.ui.settings.ThemePreferences.Values.THEME_LIGHT

/**
 * Simple theme preferences implementation.
 */
open class SimpleThemePreferences(private val context: Context) : ThemePreferences {

    private val KEY_THEME: String

    init {
        val res = context.resources
        KEY_THEME = res.getString(R.string.key_theme)
        THEME_DEFAULT = res.getString(R.string.theme_value_default)
        THEME_DARK = res.getString(R.string.theme_value_dark)
        THEME_LIGHT = res.getString(R.string.theme_value_light)
    }

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    override val themeValue: String?
        get() = preferences.getString(KEY_THEME, THEME_DEFAULT)

    override fun isDarkTheme(themeValue: String?): Boolean {
        return when (themeValue) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            else -> context.isNightMode
        }
    }

    override val isDarkTheme: Boolean
        get() = isDarkTheme(themeValue)
}