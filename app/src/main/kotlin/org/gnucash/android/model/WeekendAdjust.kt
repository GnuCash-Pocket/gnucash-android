package org.gnucash.android.model

import java.util.Locale

enum class WeekendAdjust(val value: String) {
    NONE("none"),

    /* Previous weekday */
    BACK("back"),

    /* Next weekday */
    FORWARD("forward");

    companion object {
        fun of(value: String): WeekendAdjust {
            val valueLower = value.lowercase(Locale.ROOT)
            return WeekendAdjust.values().firstOrNull { it.value == valueLower } ?: NONE
        }
    }
}