/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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

import android.os.Bundle;

import androidx.annotation.Nullable;

import org.gnucash.android.R;

/**
 * Fragment for displaying information about the application
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class AboutPreferenceFragment extends GnuPreferenceFragment {

    @Override
    protected int getTitleId() {
        return R.string.title_about_gnucash;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.fragment_about_preferences);
    }
}