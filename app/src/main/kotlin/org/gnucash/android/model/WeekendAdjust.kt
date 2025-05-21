package org.gnucash.android.model

import java.util.Locale

enum class WeekendAdjust(@JvmField val value: String?) {
    NONE("none"),

    /* Previous weekday */
    BACK("back"),

    /* Next weekday */
    FORWARD("forward");

    companion object {
        @JvmStatic
        fun of(value: String): WeekendAdjust {
            val valueLower = value.lowercase(Locale.ROOT)
            return WeekendAdjust.values().firstOrNull { it.value == valueLower } ?: NONE
        }
    }
}