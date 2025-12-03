package org.gnucash.android.test.ui.util

import android.view.View
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.Matcher
import org.hamcrest.Matchers.`is`

fun withTagValue(value: String): Matcher<View> {
    return ViewMatchers.withTagValue(`is`(value))
}
