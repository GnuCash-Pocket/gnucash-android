package org.gnucash.android.test.ui.util

import android.os.SystemClock
import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.actionWithAssertions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import org.gnucash.android.R
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import java.util.concurrent.TimeoutException

class WaitAction(private val viewToWaitFor: Matcher<View>, private val timeout: Long = TIMEOUT) :
    ViewAction {

    constructor(@IdRes viewId: Int) : this(withId(viewId))

    override fun getConstraints(): Matcher<View> {
        return any(View::class.java)
    }

    override fun getDescription(): String {
        return "wait for a view"
    }

    override fun perform(uiController: UiController, view: View) {
        uiController.loopMainThreadUntilIdle()
        val endTime = SystemClock.elapsedRealtime() + timeout
        val viewMatcher = allOf(viewToWaitFor, isDisplayed())

        do {
            for (child in TreeIterables.breadthFirstViewTraversal(view)) {
                // found view with required ID
                if (viewMatcher.matches(child)) {
                    return
                }
            }

            uiController.loopMainThreadForAtLeast(WAIT)
        } while (SystemClock.elapsedRealtime() <= endTime)

        throw PerformException.Builder()
            .withActionDescription(description)
            .withViewDescription(HumanReadables.describe(view))
            .withCause(TimeoutException())
            .build()
    }

    companion object {
        private const val WAIT = 50L
        private const val TIMEOUT = 2000L

        fun waitForView(@IdRes viewId: Int): ViewAction {
            return actionWithAssertions(WaitAction(viewId))
        }

        fun waitForView(viewMatcher: Matcher<View>): ViewAction {
            return actionWithAssertions(WaitAction(viewMatcher))
        }
    }
}
