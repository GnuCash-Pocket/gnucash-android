package org.gnucash.android.test.unit

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

    @JvmField
    protected val context = GnuCashApplication.getAppContext()

    companion object {
        @JvmStatic
        @BeforeClass
        fun before() {
            Timber.plant(ConsoleTree())
        }
    }

    protected fun openResourceStream(name: String): InputStream {
        return javaClass.classLoader.getResourceAsStream(name)
    }
}