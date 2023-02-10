package org.gnucash.android.test.unit.testutil;

import android.content.Context;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow class for crashlytics to prevent logging during testing
 */
@Implements(FirebaseCrashlytics.class)
public class ShadowCrashlytics {

    @Implementation
    public static void start(Context context) {
        System.out.println("Shadowing crashlytics start");
        //nothing to see here, move along
    }
}
