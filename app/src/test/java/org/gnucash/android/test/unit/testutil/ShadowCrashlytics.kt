package org.gnucash.android.test.unit.testutil

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Shadow class for crashlytics to prevent logging during testing
 */
@Implements(FirebaseCrashlytics::class)
class ShadowCrashlytics {
    @Implementation
    fun start(context: Context) {
        println("Shadowing crashlytics start")
        //nothing to see here, move along
    }
}
