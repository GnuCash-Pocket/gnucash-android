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
package org.gnucash.android.ui.budget

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.annotation.ColorInt
import org.gnucash.android.R
import org.gnucash.android.databinding.ActivityBudgetsBinding
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument

/**
 * Activity for managing display and editing of budgets
 */
class BudgetsActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityBudgetsBinding

    override fun inflateView() {
        val binding = ActivityBudgetsBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView
        toolbar = binding.toolbarLayout.toolbar
        toolbarProgress = binding.toolbarLayout.toolbarProgress.progress
    }

    override val titleRes: Int = R.string.title_budgets

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragmentManager = supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BudgetListFragment())
                .commit()
        }

        binding.fabCreateBudget.setOnClickListener { v ->
            onCreateBudgetClick(v)
        }
    }

    /**
     * Callback when create budget floating action button is clicked
     *
     * @param view View which was clicked
     */
    private fun onCreateBudgetClick(view: View) {
        val intent = Intent(view.context, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
        startActivity(intent)
    }

    companion object {
        /**
         * Returns a color between red and green depending on the value parameter
         *
         * @param value Value between 0 and 1 indicating the red to green ratio
         * @return Color between red and green
         */
        @ColorInt
        fun getBudgetProgressColor(value: Float): Int {
            return Color.HSVToColor(floatArrayOf(value * 120f, 1f, 0.8f))
        }
    }
}
