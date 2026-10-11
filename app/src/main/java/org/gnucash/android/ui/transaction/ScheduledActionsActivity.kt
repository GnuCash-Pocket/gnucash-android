/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.gnucash.android.R
import org.gnucash.android.databinding.ActivityScheduledEventsBinding
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.UxArgument.EXTRA_TAB_INDEX
import org.gnucash.android.ui.util.widget.FragmentStateAdapter

/**
 * Activity for displaying scheduled actions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ScheduledActionsActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityScheduledEventsBinding

    override val titleRes: Int = R.string.nav_menu_scheduled_actions

    override fun inflateView() {
        binding = ActivityScheduledEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView
        toolbar = binding.toolbarLayout.toolbar
        toolbarProgress = binding.toolbarLayout.toolbarProgress.progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tabLayout = binding.tabLayout
        repeat(NUM_PAGES) {
            tabLayout.addTab(tabLayout.newTab())
        }
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL)

        binding.pager.adapter = ScheduledActionsViewPager(this)
        TabLayoutMediator(tabLayout, binding.pager) { tab, position ->
            when (position) {
                TAB_TRANSACTIONS -> tab.setText(R.string.title_scheduled_transactions)
                TAB_EXPORTS -> tab.setText(R.string.title_scheduled_exports)
            }
        }.attach()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        setCurrentTab(intent)
    }

    /**
     * Sets the current tab in the ViewPager
     */
    private fun setCurrentTab(intent: Intent) {
        val index = intent.getIntExtra(EXTRA_TAB_INDEX, TAB_TRANSACTIONS)
        binding.pager.currentItem = index
    }

    /**
     * View pager adapter for managing the scheduled action views
     */
    private class ScheduledActionsViewPager(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                TAB_TRANSACTIONS -> ScheduledTransactionsListFragment()
                TAB_EXPORTS -> ScheduledExportsListFragment()
                else -> throw IndexOutOfBoundsException()
            }
        }

        override fun getItemCount(): Int {
            return NUM_PAGES
        }
    }

    companion object {
        /**
         * Number of pages to show
         */
        private const val NUM_PAGES = 2
        const val TAB_TRANSACTIONS = 0
        const val TAB_EXPORTS = 1

        //show scheduled transactions
        fun show(context: Context) {
            val intent = Intent(context, ScheduledActionsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
        }
    }
}
