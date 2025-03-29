package org.gnucash.android.test.ui

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kobakei.ratethisapp.RateThisApp
import org.gnucash.android.R
import org.gnucash.android.ui.account.AccountsActivity
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class GnuAndroidTest {

    /**
     * Sleep the thread for a specified period
     *
     * @param millis Duration to sleep in milliseconds
     */
    protected fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val BUTTON_POSITIVE = android.R.id.button1
        const val BUTTON_NEGATIVE = android.R.id.button2

        /**
         * Prevents the first-run dialogs (Whats new, Create accounts etc) from being displayed when testing
         *
         * @param context Application context
         */
        @JvmStatic
        fun preventFirstRunDialogs(context: Context) {
            AccountsActivity.rateAppConfig = RateThisApp.Config(10000, 10000)
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit() //do not show first run dialog
                .putBoolean(context.getString(R.string.key_first_run), false)
                .putInt(
                    AccountsActivity.LAST_OPEN_TAB_INDEX,
                    AccountsActivity.INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT
                )
                .apply()
        }
    }
}