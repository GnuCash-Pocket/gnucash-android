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
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.adapter.DefaultItemSelectedListener
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.report.ReportsActivity
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.ScheduledActionsActivity
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.util.BookUtils.activateBook
import org.gnucash.android.util.BookUtils.loadBook
import org.gnucash.android.util.chooseDocument
import org.gnucash.android.util.openBook

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

    private inner class DrawerItemClickListener : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
            onDrawerMenuItemClicked(menuItem.itemId)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inflateView()

        //if a parameter was passed to open an account within a specific book, then switch
        val bookUID = intent.getStringExtra(UxArgument.BOOK_UID)
        if (bookUID != null && bookUID != activeBookUID) {
            activateBook(this, bookUID)
        }

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(titleRes)
        }

        val headerView = navigationView!!.getHeaderView(0)
        headerView.findViewById<View>(R.id.drawer_title).setOnClickListener {
            onClickAppTitle()
        }

        bookNameSpinner = headerView.findViewById<Spinner>(R.id.book_name)
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
        val count = books.size
        var activeBookIndex = -1
        val names = mutableListOf<String>()

        for (i in 0 until count) {
            val book = books[i]
            names.add(book.displayName.orEmpty())
            if (book.isActive && (activeBookIndex < 0)) {
                activeBookIndex = i
            }
        }
        names.add(getString(R.string.menu_manage_books))

        val context: Context = ContextThemeWrapper(this, R.style.Theme_GnuCash_Toolbar)
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bookNameSpinner.adapter = adapter

        val activeBookPosition = activeBookIndex
        bookNameSpinner.setSelection(activeBookIndex)

        bookNameSpinner.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                if (position == activeBookPosition) {
                    return@DefaultItemSelectedListener
                }
                val context = view!!.context
                if (position == parent.count - 1) {
                    val intent = Intent(context, PreferenceActivity::class.java)
                    intent.setAction(PreferenceActivity.ACTION_MANAGE_BOOKS)
                    startActivity(intent)
                    drawerLayout!!.closeDrawer(navigationView!!)
                    return@DefaultItemSelectedListener
                }
                val book = books[position]
                loadBook(context, book.uid)
                finish()
                AccountsActivity.start(context)
            }
    }

    /**
     * Handler for the navigation drawer items
     */
    protected fun onDrawerMenuItemClicked(itemId: Int) {
        val context: Context = this

        when (itemId) {
            R.id.nav_item_open -> chooseDocument(REQUEST_OPEN_DOCUMENT)

            R.id.nav_item_favorites -> AccountsActivity.start(
                context,
                AccountsActivity.INDEX_FAVORITE_ACCOUNTS_FRAGMENT
            )

            R.id.nav_item_reports -> ReportsActivity.show(context)

            R.id.nav_item_scheduled_actions -> ScheduledActionsActivity.show(context)

            R.id.nav_item_export -> AccountsActivity.openExportFragment(context)

            R.id.nav_item_settings -> PreferenceActivity.show(context)

            R.id.nav_item_search -> TransactionsActivity.openSearchFragment(context)
        }
        drawerLayout!!.closeDrawer(navigationView!!)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE,
            REQUEST_OPEN_DOCUMENT -> if (resultCode == RESULT_OK && data != null) {
                openBook(this, data)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun onClickAppTitle() {
        drawerLayout!!.closeDrawer(navigationView!!)
        AccountsActivity.start(this)
    }

    companion object {
        private const val REQUEST_OPEN_DOCUMENT = 0x20
    }
}
