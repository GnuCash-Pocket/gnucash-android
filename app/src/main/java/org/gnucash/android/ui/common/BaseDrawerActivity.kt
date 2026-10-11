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
package org.gnucash.android.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.INVALID_POSITION
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import org.gnucash.android.R
import org.gnucash.android.db.NoActiveBookException
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.model.Book
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.adapter.DefaultItemSelectedListener
import org.gnucash.android.ui.adapter.SpinnerArrayAdapter
import org.gnucash.android.ui.adapter.SpinnerItem
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.price.PriceDatabaseActivity
import org.gnucash.android.ui.report.ReportsActivity
import org.gnucash.android.ui.settings.BookManagerFragment.Companion.openBook
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.ScheduledActionsActivity
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.util.BookUtils.showBook
import org.gnucash.android.util.documentMimeTypes
import timber.log.Timber

/**
 * Base activity implementing the navigation drawer, to be extended by all activities requiring one.
 *
 *
 * Each activity inheriting from this class has an indeterminate progress bar at the top,
 * (above the action bar) which can be used to display busy operations. See [.getProgressBar]
 *
 *
 *
 * Sub-classes should simply inflate their root view in [.inflateView].
 * The activity layout of the subclass is expected to contain `DrawerLayout` and
 * a `NavigationView`.<br></br>
 * Sub-class should also consider using the `toolbar.xml` or `toolbar_with_spinner.xml`
 * for the action bar in their XML layout. Otherwise provide another which contains widgets for the
 * toolbar and progress indicator with the IDs `R.id.toolbar` and `R.id.progress_indicator` respectively.
 *
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
abstract class BaseDrawerActivity : PasscodeLockActivity() {
    protected var navigationView: NavigationView? = null

    protected var drawerLayout: DrawerLayout? = null

    protected var toolbar: Toolbar? = null

    protected var toolbarProgress: ProgressBar? = null
    private var bookNameSpinner: Spinner? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    private val pickDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                openBook(this@BaseDrawerActivity, uri)
            }
        }

    private inner class DrawerItemClickListener : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
            onDrawerMenuItemClicked(menuItem.itemId)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inflateView()

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(titleRes)
        }

        val headerView = navigationView!!.getHeaderView(0)
        headerView.findViewById<View>(R.id.drawer_title).setOnClickListener {
            onClickAppTitle(headerView.context)
        }

        bookNameSpinner = headerView.findViewById(R.id.book_name)
        updateActiveBookName()
        setUpNavigationDrawer()
    }

    override fun onResume() {
        super.onResume()
        updateActiveBookName()
    }

    /**
     * Inflate the view for this activity. This method should be implemented by the sub-class.
     */
    abstract fun inflateView()

    @get:StringRes
    abstract val titleRes: Int

    /**
     * The progress bar is displayed above the toolbar and should be used to show busy status
     * for long operations.<br></br>
     * The progress bar visibility is set to [View.GONE] by default. Make visible to use
     *
     * @param isVisible Is the progress bar visible?
     */
    fun showProgressBar(isVisible: Boolean) {
        toolbarProgress?.isVisible = isVisible
    }

    /**
     * Sets up the navigation drawer for this activity.
     */
    private fun setUpNavigationDrawer() {
        navigationView!!.setNavigationItemSelectedListener(DrawerItemClickListener())

        drawerToggle = object : ActionBarDrawerToggle(
            this,  /* host Activity */
            drawerLayout,  /* DrawerLayout object */
            R.string.drawer_open,  /* "open drawer" description */
            R.string.drawer_close /* "close drawer" description */
        ) {
            /** Called when a drawer has settled in a completely closed state.  */
            override fun onDrawerClosed(view: View) {
                super.onDrawerClosed(view)
            }

            /** Called when a drawer has settled in a completely open state.  */
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
            }
        }

        drawerLayout!!.setDrawerListener(drawerToggle)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle!!.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle!!.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val drawerLayout = drawerLayout!!
            val navigationView = navigationView!!
            if (!drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.openDrawer(navigationView)
            } else {
                drawerLayout.closeDrawer(navigationView)
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Update the display name of the currently active book
     */
    protected fun updateActiveBookName() {
        val bookNameSpinner = bookNameSpinner!!
        val books = BooksDbAdapter.instance.allRecords
        val bookItems = mutableListOf<SpinnerItem<Book>>()
        var activeBookIndex = INVALID_POSITION

        for ((i, book) in books.withIndex()) {
            bookItems.add(SpinnerItem(book, book.displayName.orEmpty()))
            if ((book.uid == bookUID) && (activeBookIndex < 0)) {
                activeBookIndex = i
            }
        }

        val context: Context = ContextThemeWrapper(this, R.style.Theme_GnuCash_Toolbar)
        val adapter = SpinnerArrayAdapter(context, bookItems)
        bookNameSpinner.adapter = adapter
        bookNameSpinner.setSelection(activeBookIndex)

        bookNameSpinner.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                if (view == null) return@DefaultItemSelectedListener
                if (position == activeBookIndex) return@DefaultItemSelectedListener
                val context = view.context
                val book = bookItems[position].value
                showBook(context, book.uid)
                finish()
            }
    }

    /**
     * Handler for the navigation drawer items
     */
    protected fun onDrawerMenuItemClicked(itemId: Int) {
        val context: Context = this
        val drawerLayout = drawerLayout ?: return
        val navigationView = navigationView ?: return

        when (itemId) {
            R.id.nav_item_books -> showBooks(context)

            R.id.nav_item_open -> pickDocumentLauncher.launch(documentMimeTypes)

            R.id.nav_item_favorites -> showFavorites(context, bookUID)

            R.id.nav_item_reports -> ReportsActivity.show(context, bookUID)

            R.id.nav_item_scheduled_actions -> ScheduledActionsActivity.show(context, bookUID)

            R.id.nav_item_export -> AccountsActivity.openExportFragment(context, bookUID)

            R.id.nav_item_prices -> PriceDatabaseActivity.show(context, bookUID)

            R.id.nav_item_settings -> PreferenceActivity.show(context, bookUID)

            R.id.nav_item_search -> TransactionsActivity.openSearchFragment(context, bookUID)
        }
        drawerLayout.closeDrawer(navigationView)
    }

    fun onClickAppTitle(context: Context) {
        showFavorites(context, bookUID)
    }

    private fun showFavorites(context: Context, bookUID: String) {
        val drawerLayout = drawerLayout ?: return
        val navigationView = navigationView ?: return

        drawerLayout.closeDrawer(navigationView)
        try {
            AccountsActivity.start(
                context,
                bookUID,
                AccountsActivity.INDEX_FAVORITE_ACCOUNTS_FRAGMENT
            )
        } catch (e: NoActiveBookException) {
            Timber.e(e)
        }
    }

    private fun showBooks(context: Context) {
        drawerLayout!!.closeDrawer(navigationView!!)
        PreferenceActivity.showBooks(context, bookUID)
    }
}
