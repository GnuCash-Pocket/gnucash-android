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

package org.gnucash.android.ui.passcode;

import android.content.Intent;
import android.os.Bundle;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashActivity;
import org.gnucash.android.ui.settings.ThemeHelper;

/**
 * Activity for entering and confirming passcode
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class PasscodePreferenceActivity extends GnuCashActivity {

    public static final String DISABLE_PASSCODE = PasscodeModifyFragment.DISABLE_PASSCODE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.apply(this);
        setContentView(R.layout.passcode_lockscreen);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        Bundle args = new Bundle();
        args.putAll((extras != null) ? extras : new Bundle());
        PasscodeModifyFragment fragment = new PasscodeModifyFragment();
        fragment.setArguments(args);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit();
    }
}
