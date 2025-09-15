/*
 * Copyright (c) 2014 - 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

import android.os.Bundle
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashActivity
import org.gnucash.android.ui.settings.ThemeHelper

/**
 * Activity for entering and confirming passcode
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class PasscodePreferenceActivity : GnuCashActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.apply(this)
        setContentView(R.layout.passcode_lockscreen)

        val args = Bundle()
        args.putAll(intent.extras ?: Bundle())
        val fragment = PasscodeModifyFragment()
        fragment.arguments = args

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    companion object {
        const val DISABLE_PASSCODE: String = PasscodeModifyFragment.DISABLE_PASSCODE
    }
}
