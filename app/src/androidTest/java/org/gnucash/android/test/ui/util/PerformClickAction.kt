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

class PerformClickAction : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return allOf(isDisplayed(), isEnabled(), isClickable())
    }

    override fun getDescription(): String? {
        return "Perform a click action"
    }

    override fun perform(uiController: UiController, view: View) {
        view.performClick()
    }

    companion object {
        fun click(): ViewAction {
            return actionWithAssertions(
                PerformClickAction()
            )
        }
    }
}
