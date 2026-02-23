package org.gnucash.android.test.ui.util

import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.actionWithAssertions
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not

class PerformEnableAction : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return allOf(isDisplayed(), not(isEnabled()))
    }

    override fun getDescription(): String {
        return "Enable"
    }

    override fun perform(uiController: UiController, view: View) {
        view.isEnabled = true
    }

    companion object {
        fun enable(): ViewAction {
            return actionWithAssertions(
                PerformEnableAction()
            )
        }
    }
}
