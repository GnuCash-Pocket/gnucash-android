package org.gnucash.android.test.unit

import org.gnucash.android.test.unit.testutil.ShadowCrashlytics
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class) //package is required so that resources can be found in dev mode
@Config(sdk = [21], shadows = [ShadowCrashlytics::class])
abstract class GnuCashTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun before() {
            Timber.plant(ConsoleTree())
        }
    }
}