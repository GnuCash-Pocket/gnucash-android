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
package org.gnucash.android.ui.common;

import static org.gnucash.android.util.DocumentExtKt.chooseDocument;
import static org.gnucash.android.util.DocumentExtKt.openBook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.model.Book;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.passcode.PasscodeLockActivity;
import org.gnucash.android.ui.report.ReportsActivity;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.ui.transaction.ScheduledActionsActivity;
import org.gnucash.android.util.BookUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Base activity implementing the navigation drawer, to be extended by all activities requiring one.
 * <p>
 * Each activity inheriting from this class has an indeterminate progress bar at the top,
 * (above the action bar) which can be used to display busy operations. See {@link #getProgressBar()}
 * </p>
 *
 * <p>Sub-classes should simply inflate their root view in {@link #inflateView()}.
 * The activity layout of the subclass is expected to contain {@code DrawerLayout} and
 * a {@code NavigationView}.<br>
 * Sub-class should also consider using the {@code toolbar.xml} or {@code toolbar_with_spinner.xml}
 * for the action bar in their XML layout. Otherwise provide another which contains widgets for the
 * toolbar and progress indicator with the IDs {@code R.id.toolbar} and {@code R.id.progress_indicator} respectively.
 * </p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public abstract class BaseDrawerActivity extends PasscodeLockActivity {

    protected DrawerLayout mDrawerLayout;
    protected NavigationView mNavigationView;
    protected Toolbar mToolbar;
    protected ProgressBar mToolbarProgress;
    protected Spinner mBookNameSpinner;

    protected ActionBarDrawerToggle mDrawerToggle;

    private static final int REQUEST_OPEN_DOCUMENT = 0x20;

    private class DrawerItemClickListener implements NavigationView.OnNavigationItemSelectedListener {

        @Override
        public boolean onNavigationItemSelected(MenuItem menuItem) {
            onDrawerMenuItemClicked(menuItem.getItemId());
            return true;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inflateView();

        //if a parameter was passed to open an account within a specific book, then switch
        String bookUID = getIntent().getStringExtra(UxArgument.BOOK_UID);
        if (bookUID != null && !bookUID.equals(GnuCashApplication.getActiveBookUID())) {
            BookUtils.activateBook(this, bookUID);
        }

        setSupportActionBar(mToolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getTitleRes());
        }

        View headerView = mNavigationView.getHeaderView(0);
        headerView.findViewById(R.id.drawer_title).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickAppTitle(v);
            }
        });

        mBookNameSpinner = headerView.findViewById(R.id.book_name);
        updateActiveBookName();
        setUpNavigationDrawer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateActiveBookName();
    }

    /**
     * Inflate the view for this activity. This method should be implemented by the sub-class.
     */
    public abstract void inflateView();

    /**
     * Return the title for this activity.
     * This will be displayed in the action bar
     *
     * @return String resource identifier
     */
    public abstract @StringRes int getTitleRes();

    /**
     * The progress bar is displayed above the toolbar and should be used to show busy status
     * for long operations.<br/>
     * The progress bar visibility is set to {@link View#GONE} by default. Make visible to use </p>
     *
     * @param isVisible Is the progress bar visible?
     */
    public void showProgressBar(boolean isVisible) {
        mToolbarProgress.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets up the navigation drawer for this activity.
     */
    private void setUpNavigationDrawer() {
        mNavigationView.setNavigationItemSelectedListener(new DrawerItemClickListener());

        mDrawerToggle = new ActionBarDrawerToggle(
            this,                  /* host Activity */
            mDrawerLayout,         /* DrawerLayout object */
            R.string.drawer_open,  /* "open drawer" description */
            R.string.drawer_close  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!mDrawerLayout.isDrawerOpen(mNavigationView))
                mDrawerLayout.openDrawer(mNavigationView);
            else
                mDrawerLayout.closeDrawer(mNavigationView);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Update the display name of the currently active book
     */
    protected void updateActiveBookName() {
        final List<Book> books = BooksDbAdapter.getInstance().getAllRecords();
        final int count = books.size();
        int activeBookIndex = -1;
        List<String> names = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Book book = books.get(i);
            names.add(book.getDisplayName());
            if (book.isActive() && (activeBookIndex < 0)) {
                activeBookIndex = i;
            }
        }
        names.add(getString(R.string.menu_manage_books));

        Context context = new ContextThemeWrapper(this, R.style.Theme_GnuCash_Toolbar);
        String[] entries = names.toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookNameSpinner.setAdapter(adapter);

        final int activeBookPosition = activeBookIndex;
        mBookNameSpinner.setSelection(activeBookIndex);

        mBookNameSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == activeBookPosition) {
                    return;
                }
                final Context context = view.getContext();
                if (position == parent.getCount() - 1) {
                    Intent intent = new Intent(context, PreferenceActivity.class);
                    intent.setAction(PreferenceActivity.ACTION_MANAGE_BOOKS);
                    startActivity(intent);
                    mDrawerLayout.closeDrawer(mNavigationView);
                    return;
                }
                Book book = books.get(position);
                BookUtils.loadBook(context, book.getUID());
                finish();
                AccountsActivity.start(context);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * Handler for the navigation drawer items
     */
    protected void onDrawerMenuItemClicked(int itemId) {
        switch (itemId) {
            case R.id.nav_item_open:  //Open... files
                chooseDocument(this, REQUEST_OPEN_DOCUMENT);
                break;

            case R.id.nav_item_favorites:  //favorite accounts
                AccountsActivity.start(this, AccountsActivity.INDEX_FAVORITE_ACCOUNTS_FRAGMENT);
                break;

            case R.id.nav_item_reports: {
                Intent intent = new Intent(this, ReportsActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
            break;

/*
            //todo: Re-enable this when Budget UI is complete
            case R.id.nav_item_budgets:
                startActivity(new Intent(this, BudgetsActivity.class));
                break;
*/
            case R.id.nav_item_scheduled_actions: { //show scheduled transactions
                Intent intent = new Intent(this, ScheduledActionsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
            break;

            case R.id.nav_item_export:
                AccountsActivity.openExportFragment(this);
                break;

            case R.id.nav_item_settings: //Settings activity
                startActivity(new Intent(this, PreferenceActivity.class));
                break;

            //case R.id.nav_item_help:
            //    PasscodeHelper.skipPasscodeScreen(this);
            //    break;
        }
        mDrawerLayout.closeDrawer(mNavigationView);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE:
            case BaseDrawerActivity.REQUEST_OPEN_DOCUMENT: //this uses the Storage Access Framework
                if (resultCode == Activity.RESULT_OK && data != null) {
                    openBook(this, data);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    public void onClickAppTitle(View view) {
        mDrawerLayout.closeDrawer(mNavigationView);
        AccountsActivity.start(this);
    }
}
