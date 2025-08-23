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

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.TabLayoutOnPageChangeListener
import org.gnucash.android.R
import org.gnucash.android.databinding.ActivityScheduledEventsBinding
import org.gnucash.android.ui.common.BaseDrawerActivity

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
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_scheduled_transactions))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_scheduled_exports))
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL)

        val viewPager = binding.pager

        //show the simple accounts list
        viewPager.adapter = ScheduledActionsViewPager(supportFragmentManager)
        viewPager.addOnPageChangeListener(TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }


    /**
     * View pager adapter for managing the scheduled action views
     */
    private inner class ScheduledActionsViewPager(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm) {
        override fun getPageTitle(position: Int): CharSequence? {
            return when (position) {
                INDEX_SCHEDULED_TRANSACTIONS -> getString(R.string.title_scheduled_transactions)
                INDEX_SCHEDULED_EXPORTS -> getString(R.string.title_scheduled_exports)
                else -> super.getPageTitle(position)
            }
        }

        override fun getItem(position: Int): Fragment {
            when (position) {
                INDEX_SCHEDULED_TRANSACTIONS -> return ScheduledTransactionsListFragment()
                INDEX_SCHEDULED_EXPORTS -> return ScheduledExportsListFragment()
            }
            throw IndexOutOfBoundsException()
        }

        override fun getCount(): Int {
            return 2
        }
    }

    companion object {
        private const val INDEX_SCHEDULED_TRANSACTIONS = 0
        private const val INDEX_SCHEDULED_EXPORTS = 1
    }
}
