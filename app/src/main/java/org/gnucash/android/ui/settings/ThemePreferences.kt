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

import androidx.annotation.StyleRes

/**
 * Theme preferences.
 */
interface ThemePreferences {
    object Values {
        /** Default theme. */
        var THEME_DEFAULT: String? = "default"

        /** Dark theme. */
        var THEME_DARK: String? = "dark"

        /** Light theme. */
        var THEME_LIGHT: String? = "light"
    }

    /**
     * Get the theme value.
     *
     * @return the theme value.
     */
    val themeValue: String?

    /**
     * Is the theme dark?
     *
     * @param themeValue the theme value.
     * @return `true` if the theme has dark backgrounds and light texts.
     */
    fun isDarkTheme(themeValue: String?): Boolean

    /**
     * Is the theme dark?
     *
     * @return `true` if the theme has dark backgrounds and light texts.
     */
    val isDarkTheme: Boolean
}