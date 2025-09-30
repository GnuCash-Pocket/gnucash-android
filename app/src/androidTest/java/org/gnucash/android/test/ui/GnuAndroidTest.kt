package org.gnucash.android.test.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kobakei.ratethisapp.RateThisApp
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.util.applyLocale
import org.hamcrest.Matchers.not
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class GnuAndroidTest {

    @JvmField
    protected val context = GnuCashApplication.appContext

    val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

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

    /**
     * Checks that a specific toast message is displayed
     *
     * @param toastString String that should be displayed
     */
    fun assertToastDisplayed(activity: Activity, @StringRes toastString: Int) {
        assertToastDisplayed(activity, activity.getString(toastString))
    }

    /**
     * Checks that a specific toast message is displayed
     *
     * @param toastString String that should be displayed
     */
    fun assertToastDisplayed(activity: Activity, toastString: String) {
        device.waitForIdle()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onView(withText(toastString))
                .inRoot(withDecorView(not(activity.window.decorView)))
                .check(matches(isDisplayed()))
        } else {
            // FIXME: see https://github.com/android/android-test/issues/803
            // assertThat(device.hasObject(By.text(toastString))).isTrue()
        }
    }

    /**
     * Simple wrapper for clicking on views with espresso
     *
     * @param viewId View resource ID
     */
    fun clickViewId(@IdRes viewId: Int): ViewInteraction {
        return onView(withId(viewId))
            .perform(click())
    }

    /**
     * Simple wrapper for clicking on views in a dialog with espresso
     *
     * @param viewId View resource ID
     */
    fun clickViewDialog(@IdRes viewId: Int): ViewInteraction {
        return onView(withId(viewId))
            .inRoot(isDialog())
            .perform(click())
    }

    /**
     * Simple wrapper for clicking on views with espresso
     *
     * @param textId String resource ID
     */
    fun clickViewText(@StringRes textId: Int): ViewInteraction {
        return onView(withText(textId))
            .perform(click())
    }

    /**
     * Simple wrapper for clicking on views with espresso
     *
     * @param text The text.
     */
    fun clickViewText(text: String?): ViewInteraction {
        return onView(withText(text))
            .perform(click())
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
                .edit { //do not show first run dialog
                    putBoolean(context.getString(R.string.key_first_run), false)
                    putInt(
                        AccountsActivity.LAST_OPEN_TAB_INDEX,
                        AccountsActivity.INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT
                    )
                }
        }

        /**
         * Prevents the first-run dialogs (Whats new, Create accounts etc) from being displayed when testing
         */
        @JvmStatic
        fun preventFirstRunDialogs() {
            val context = GnuCashApplication.appContext
            preventFirstRunDialogs(context)
        }

        /**
         * Configure the device for standard configuration.
         */
        @JvmStatic
        fun configureDevice() {
            val context = GnuCashApplication.appContext
            context.applyLocale(Locale.US)
        }
    }
}