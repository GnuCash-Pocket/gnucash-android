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
package org.gnucash.android.ui.budget;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.FragmentManager;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityBudgetsBinding;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;

/**
 * Activity for managing display and editing of budgets
 */
public class BudgetsActivity extends BaseDrawerActivity {

    public static final int REQUEST_CREATE_BUDGET = 0xA;

    private ActivityBudgetsBinding binding;

    @Override
    public void inflateView() {
        binding = ActivityBudgetsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mDrawerLayout = binding.drawerLayout;
        mNavigationView = binding.navView;
        mToolbar = binding.toolbarLayout.toolbar;
        mToolbarProgress = binding.toolbarLayout.toolbarProgress.progress;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_budgets;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, new BudgetListFragment())
                .commit();
        }
    }

    /**
     * Callback when create budget floating action button is clicked
     *
     * @param view View which was clicked
     */
    public void onCreateBudgetClick(View view) {
        Intent addAccountIntent = new Intent(BudgetsActivity.this, FormActivity.class);
        addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name());
        startActivityForResult(addAccountIntent, REQUEST_CREATE_BUDGET);
    }

    /**
     * Returns a color between red and green depending on the value parameter
     *
     * @param value Value between 0 and 1 indicating the red to green ratio
     * @return Color between red and green
     */
    public static int getBudgetProgressColor(double value) {
        return GnuCashApplication.darken(android.graphics.Color.HSVToColor(new float[]{(float) value * 120f, 1f, 1f}));
    }
}
