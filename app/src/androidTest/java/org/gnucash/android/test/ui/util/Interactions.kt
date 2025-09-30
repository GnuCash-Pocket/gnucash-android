package org.gnucash.android.test.ui.util

import androidx.test.espresso.DataInteraction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click

fun DataInteraction.performClick(): ViewInteraction {
    return perform(click())
}

fun ViewInteraction.performClick(): ViewInteraction {
    return perform(click())
}
