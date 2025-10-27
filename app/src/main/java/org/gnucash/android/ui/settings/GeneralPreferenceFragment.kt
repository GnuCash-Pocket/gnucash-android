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
package org.gnucash.android.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import org.gnucash.android.R
import org.gnucash.android.app.restart
import org.gnucash.android.ui.passcode.PasscodeHelper
import org.gnucash.android.ui.passcode.PasscodePreferenceActivity
import org.gnucash.android.ui.snackLong

/**
 * Fragment for general preferences. Currently caters to the passcode and reporting preferences
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class GeneralPreferenceFragment : GnuPreferenceFragment() {
    private lateinit var preferencePasscode: TwoStatePreference

    override val titleId: Int = R.string.title_general_prefs

    override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.fragment_general_preferences)

        val preferenceTheme = findPreference<Preference>(getString(R.string.key_theme))!!
        preferenceTheme.setOnPreferenceChangeListener { preference, newValue ->
            requireActivity().restart()
            true
        }

        preferencePasscode =
            findPreference(getString(R.string.key_enable_passcode))!!
        preferencePasscode.isChecked = PasscodeHelper.isPasscodeEnabled(preferencePasscode.context)
        preferencePasscode.setOnPreferenceChangeListener { preference, newValue ->
            val context = preference.context
            if (newValue as Boolean) {
                val intent = Intent(context, PasscodePreferenceActivity::class.java)
                startActivityForResult(intent, PASSCODE_REQUEST_CODE)
            } else {
                val intent = Intent(context, PasscodePreferenceActivity::class.java)
                    .putExtra(PasscodePreferenceActivity.DISABLE_PASSCODE, true)
                startActivityForResult(intent, REQUEST_DISABLE_PASSCODE)
            }
            true
        }

        val preferencePasscodeChange =
            findPreference<Preference>(getString(R.string.key_change_passcode))!!
        preferencePasscodeChange.setOnPreferenceClickListener { preference ->
            val context = preference.context
            val intent = Intent(context, PasscodePreferenceActivity::class.java)
            startActivityForResult(intent, REQUEST_CHANGE_PASSCODE)
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val context = requireContext()
        preferencePasscode.isChecked = PasscodeHelper.isPasscodeEnabled(context)

        when (requestCode) {
            PASSCODE_REQUEST_CODE -> if (resultCode == Activity.RESULT_OK) {
                snackLong(R.string.toast_passcode_set)
            }

            REQUEST_CHANGE_PASSCODE -> if (resultCode == Activity.RESULT_OK) {
                snackLong(R.string.toast_passcode_set)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        /**
         * Request code for retrieving passcode to store
         */
        const val PASSCODE_REQUEST_CODE = 2

        /**
         * Request code for disabling passcode
         */
        const val REQUEST_DISABLE_PASSCODE = 3

        /**
         * Request code for changing passcode
         */
        const val REQUEST_CHANGE_PASSCODE = 4
    }
}
