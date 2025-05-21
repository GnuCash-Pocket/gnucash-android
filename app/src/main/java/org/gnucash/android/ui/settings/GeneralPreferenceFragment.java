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

package org.gnucash.android.ui.settings;

import static org.gnucash.android.app.ActivityExtKt.restart;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.gnucash.android.R;
import org.gnucash.android.ui.passcode.PasscodePreferenceActivity;

/**
 * Fragment for general preferences. Currently caters to the passcode and reporting preferences
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class GeneralPreferenceFragment extends PreferenceFragmentCompat {

    /**
     * Request code for retrieving passcode to store
     */
    public static final int PASSCODE_REQUEST_CODE = 0x2;
    /**
     * Request code for disabling passcode
     */
    public static final int REQUEST_DISABLE_PASSCODE = 0x3;
    /**
     * Request code for changing passcode
     */
    public static final int REQUEST_CHANGE_PASSCODE = 0x4;

    private SwitchPreference preferencePasscode;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_general_prefs);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        addPreferencesFromResource(R.xml.fragment_general_preferences);

        findPreference(getString(R.string.key_theme)).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                restart(requireActivity());
                return true;
            }
        });

        preferencePasscode = findPreference(getString(R.string.key_enable_passcode));
        preferencePasscode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                Context context = preference.getContext();
                if ((Boolean) newValue) {
                    Intent intent = new Intent(context, PasscodePreferenceActivity.class);
                    startActivityForResult(intent, PASSCODE_REQUEST_CODE);
                } else {
                    Intent intent = new Intent(context, PasscodePreferenceActivity.class)
                        .putExtra(PasscodePreferenceActivity.DISABLE_PASSCODE, true);
                    startActivityForResult(intent, REQUEST_DISABLE_PASSCODE);
                }
                return true;
            }
        });

        Preference preferencePasscodeChange = findPreference(getString(R.string.key_change_passcode));
        preferencePasscodeChange.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                Context context = preference.getContext();
                Intent intent = new Intent(context, PasscodePreferenceActivity.class);
                startActivityForResult(intent, REQUEST_CHANGE_PASSCODE);
                return true;
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Context context = requireContext();

        switch (requestCode) {
            case PASSCODE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(context, R.string.toast_passcode_set, Toast.LENGTH_SHORT).show();
                } else {
                    preferencePasscode.setChecked(false);
                }
                break;
            case REQUEST_DISABLE_PASSCODE:
                preferencePasscode.setChecked(resultCode != Activity.RESULT_OK);
                break;
            case REQUEST_CHANGE_PASSCODE:
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(context, R.string.toast_passcode_set, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

}
