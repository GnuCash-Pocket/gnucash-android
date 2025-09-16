/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.recyclerview.widget.LinearLayoutManager
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentScheduledEventsListBinding
import org.gnucash.android.ui.common.Refreshable

/**
 * Fragment which displays the scheduled actions in the system
 *
 * Currently, it handles the display of scheduled transactions and scheduled exports
 */
abstract class ScheduledActionsListFragment : MenuFragment(), Refreshable {
    private var listAdapter: ScheduledAdapter<*>? = null

    protected var binding: FragmentScheduledEventsListBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentScheduledEventsListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val actionBar: ActionBar? = this.actionBar
        actionBar?.setDisplayShowTitleEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setHomeButtonEnabled(true)

        val binding = binding!!
        binding.list.emptyView = binding.empty
        binding.list.layoutManager = LinearLayoutManager(view.context)
        binding.list.adapter = listAdapter
    }

    override fun refresh() {
        if (isDetached || fragmentManager == null) return
        listAdapter = (listAdapter ?: createAdapter()).also { adapter ->
            adapter.load(this@ScheduledActionsListFragment)
            binding?.list?.adapter = adapter
        }
    }

    override fun refresh(uid: String?) {
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refresh()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    protected abstract fun createAdapter(): ScheduledAdapter<*>
}

