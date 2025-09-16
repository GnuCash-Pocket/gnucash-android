package org.gnucash.android.test.ui.util

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import timber.log.Timber
import java.io.IOException

object SoftwareKeyboard {
    val isKeyboardOpen: Boolean
        get() {
            val command = "dumpsys input_method | grep mInputShown"
            try {
                return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand(command).contains("mInputShown=true")
            } catch (e: IOException) {
                Timber.e(e)
                throw RuntimeException("Keyboard state cannot be read", e)
            }
        }
}
