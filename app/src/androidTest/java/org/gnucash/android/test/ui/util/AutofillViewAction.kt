package org.gnucash.android.test.ui.util

import android.os.Build
import android.view.View
import android.view.autofill.AutofillManager
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.actionWithAssertions
import org.hamcrest.Matcher
import org.hamcrest.Matchers.any
import java.util.Locale


class AutofillViewAction(private val mode: Int) : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return any(View::class.java)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getDescription(): String {
        return String.format(Locale.ROOT, "autofill(%s)", mode.toHexString())
    }

    override fun perform(uiController: UiController, view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setImportantForAutofill(mode)

            val context = view.context
            val autofillManager = context.getSystemService(AutofillManager::class.java)
            autofillManager?.disableAutofillServices()
            autofillManager?.cancel()
        }
    }

    companion object {
        @JvmStatic
        fun disableAutofill(): ViewAction {
            return actionWithAssertions(AutofillViewAction(View.IMPORTANT_FOR_AUTOFILL_NO))
        }
    }
}
