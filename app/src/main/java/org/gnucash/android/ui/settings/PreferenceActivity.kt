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
package org.gnucash.android.ui.settings

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.databinding.ActivitySettingsBinding
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import timber.log.Timber

/**
 * Activity for unified preferences
 */
class PreferenceActivity : PasscodeLockActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null || supportFragmentManager.fragments.isEmpty()) {
            val action = intent.action
            if (action != null && action == ACTION_MANAGE_BOOKS) {
                loadFragment(BookManagerFragment(), false)
            } else {
                loadFragment(PreferenceHeadersFragment(), false)
            }
        }

        val actionBar: ActionBar? = supportActionBar
        actionBar?.setTitle(R.string.title_settings)
        actionBar?.setHomeButtonEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        if (SDK_INT >= 33) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPressed()
                }
            })
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragmentClassName = pref.fragment
        if (fragmentClassName.isNullOrEmpty()) return false
        try {
            val factory = supportFragmentManager.getFragmentFactory()
            val fragment = factory.instantiate(classLoader, fragmentClassName)
            loadFragment(fragment, true)
            return true
        } catch (e: Fragment.InstantiationException) {
            Timber.e(e)
            //if we do not have a matching class, do nothing
        }
        return false
    }

    /**
     * Load the provided fragment into the right pane, replacing the previous one
     *
     * @param fragment BaseReportFragment instance
     */
    private fun loadFragment(fragment: Fragment, stack: Boolean) {
        val fragmentManager = supportFragmentManager
        val tx = fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
        if (stack) tx.addToBackStack(null)
        tx.commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                handleBackPressed()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        handleBackPressed()
    }

    private fun handleBackPressed() {
        val fragmentManager = supportFragmentManager
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()

            val actionBar: ActionBar? = supportActionBar
            actionBar?.setTitle(R.string.title_settings)
        } else {
            finish()
        }
    }

    companion object {
        const val ACTION_MANAGE_BOOKS: String = BuildConfig.APPLICATION_ID + ".action.MANAGE_BOOKS"
    }
}
