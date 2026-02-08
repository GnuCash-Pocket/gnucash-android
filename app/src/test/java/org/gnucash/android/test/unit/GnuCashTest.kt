package org.gnucash.android.test.unit

import androidx.core.content.edit
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.InputStream

@RunWith(RobolectricTestRunner::class) //package is required so that resources can be found in dev mode
@Config(sdk = [21], shadows = [ShadowCrashlytics::class])
abstract class GnuCashTest {

    protected val context = GnuCashApplication.appContext

    companion object {
        @JvmStatic
        @BeforeClass
        fun before() {
            Timber.plant(ConsoleTree())
        }
    }

    protected fun openResourceStream(name: String): InputStream {
        return javaClass!!.classLoader!!.getResourceAsStream(name)
    }

    protected fun setDoubleEntryEnabled(enabled: Boolean) {
        GnuCashApplication.getBookPreferences(context).edit {
            putBoolean(context.getString(R.string.key_use_double_entry), enabled)
        }
    }
}