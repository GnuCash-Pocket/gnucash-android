package org.gnucash.android.ui.passcode

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.text.format.DateUtils
import org.gnucash.android.ui.common.UxArgument

object PasscodeHelper {

    /**
     * Lifetime of passcode session
     */
    const val SESSION_TIMEOUT = 5 * DateUtils.SECOND_IN_MILLIS

    /**
     * Init time of passcode session
     */
    @JvmField
    var PASSCODE_SESSION_INIT_TIME: Long = 0L

    /**
     * Key for checking whether the passcode is enabled or not
     */
    private const val ENABLED_PASSCODE = "enabled_passcode"

    /**
     * Key for storing the passcode
     */
    const val PASSCODE: String = "passcode"

    private fun getPreferences(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * @return `true` if passcode session is active, and `false` otherwise
     */
    @JvmStatic
    fun isSessionActive(): Boolean {
        return System.currentTimeMillis() < PASSCODE_SESSION_INIT_TIME + SESSION_TIMEOUT
    }

    @JvmStatic
    fun isPasscodeEnabled(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(ENABLED_PASSCODE, false)
    }

    @JvmStatic
    fun getPasscode(context: Context): String? {
        val prefs = getPreferences(context)
        return prefs.getString(PASSCODE, null)
    }

    @JvmStatic
    fun setPasscode(context: Context, value: String?) {
        val prefs = getPreferences(context)
        prefs.edit()
            .putString(PASSCODE, value)
            .putBoolean(ENABLED_PASSCODE, !value.isNullOrEmpty())
            .apply()
    }

    @JvmStatic
    fun skipPasscodeScreen(context: Context) {
        val prefs = getPreferences(context)
        prefs.edit().putBoolean(UxArgument.SKIP_PASSCODE_SCREEN, true).apply()
    }

    @JvmStatic
    fun isSkipPasscodeScreen(context: Context): Boolean {
        val prefs = getPreferences(context)
        val skipPasscode = prefs.getBoolean(UxArgument.SKIP_PASSCODE_SCREEN, false)
        prefs.edit().remove(UxArgument.SKIP_PASSCODE_SCREEN).apply()
        return skipPasscode
    }
}