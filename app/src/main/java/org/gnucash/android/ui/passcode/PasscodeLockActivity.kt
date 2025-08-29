/*
 * Copyright (c) 2014-2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.passcode

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import org.gnucash.android.app.GnuCashActivity
import org.gnucash.android.ui.passcode.PasscodeHelper.getPasscode
import org.gnucash.android.ui.passcode.PasscodeHelper.isPasscodeEnabled
import org.gnucash.android.ui.passcode.PasscodeHelper.isSessionActive
import org.gnucash.android.ui.passcode.PasscodeHelper.isSkipPasscodeScreen
import org.gnucash.android.ui.settings.ThemeHelper
import timber.log.Timber

/**
 * This activity used as the parent class for enabling passcode lock
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
open class PasscodeLockActivity : GnuCashActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.apply(this)
    }

    override fun onResume() {
        super.onResume()

        val isPassEnabled = isPasscodeEnabled(this)
        val passCode = getPasscode(this)
        // see ExportFormFragment.onPause()
        val skipPasscode = isSkipPasscodeScreen(this)

        if (isPassEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        // Only for Android Lollipop that brings a few changes to the recent apps feature
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            PasscodeHelper.passcodeSessionTime = 0
        }

        if (isPassEnabled && !skipPasscode && !isSessionActive() && !passCode.isNullOrEmpty()) {
            Timber.v("Show passcode screen")
            var args = intent.extras ?: Bundle()
            val intent = Intent(this, PasscodeLockScreenActivity::class.java)
                .setAction(intent.action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(PasscodeFragment.PASSCODE_CLASS_CALLER, this.javaClass.getName())
                .putExtras(args)
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        PasscodeHelper.passcodeSessionTime = SystemClock.elapsedRealtime()
    }
}
