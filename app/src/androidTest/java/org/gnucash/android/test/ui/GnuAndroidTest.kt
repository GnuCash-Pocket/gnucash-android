package org.gnucash.android.test.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class GnuAndroidTest {
    companion object {
        const val BUTTON_POSITIVE = android.R.id.button1
        const val BUTTON_NEGATIVE = android.R.id.button2
    }
}