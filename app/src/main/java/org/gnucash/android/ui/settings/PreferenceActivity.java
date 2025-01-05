/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.os.BuildCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivitySettingsBinding;
import org.gnucash.android.ui.passcode.PasscodeLockActivity;

import timber.log.Timber;

/**
 * Activity for unified preferences
 */
public class PreferenceActivity extends PasscodeLockActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    public static final String ACTION_MANAGE_BOOKS = BuildConfig.APPLICATION_ID + ".action.MANAGE_BOOKS";

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null || getSupportFragmentManager().getFragments().isEmpty()) {
            String action = getIntent().getAction();
            if (action != null && action.equals(ACTION_MANAGE_BOOKS)) {
                loadFragment(new BookManagerFragment(), false);
            } else {
                loadFragment(new PreferenceHeadersFragment(), false);
            }
        }

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_settings);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (BuildCompat.isAtLeastT()) {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBackPressed();
                }
            });
        }
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        String fragmentClassName = pref.getFragment();
        if (TextUtils.isEmpty(fragmentClassName)) return false;
        try {
            FragmentFactory factory = getSupportFragmentManager().getFragmentFactory();
            Fragment fragment = factory.instantiate(getClassLoader(), fragmentClassName);
            loadFragment(fragment, true);
            return true;
        } catch (Fragment.InstantiationException e) {
            Timber.e(e);
            //if we do not have a matching class, do nothing
        }
        return false;
    }

    /**
     * Load the provided fragment into the right pane, replacing the previous one
     *
     * @param fragment BaseReportFragment instance
     */
    private void loadFragment(Fragment fragment, boolean stack) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction tx = fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment);
        if (stack) tx.addToBackStack(null);
        tx.commit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    private void handleBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();

            ActionBar actionBar = getSupportActionBar();
            assert actionBar != null;
            actionBar.setTitle(R.string.title_settings);
        } else {
            finish();
        }
    }

    /**
     * Returns the shared preferences file for the currently active book.
     * Should be used instead of {@link PreferenceManager#getDefaultSharedPreferences(Context)}
     *
     * @return Shared preferences file
     */
    public static SharedPreferences getActiveBookSharedPreferences() {
        return getBookSharedPreferences(GnuCashApplication.getActiveBookUID());
    }

    /**
     * Return the {@link SharedPreferences} for a specific book
     *
     * @param bookUID GUID of the book
     * @return Shared preferences
     */
    public static SharedPreferences getBookSharedPreferences(String bookUID) {
        Context context = GnuCashApplication.getAppContext();
        return context.getSharedPreferences(bookUID, Context.MODE_PRIVATE);
    }
}
