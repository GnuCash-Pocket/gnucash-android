/*
 * Copyright (c) 2016 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.test.ui

import android.Manifest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.GrantPermissionRule
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.model.Book
import org.gnucash.android.test.ui.util.DisableAnimationsRule
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.settings.PreferenceActivity
import org.hamcrest.Matchers.allOf
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Test support for multiple books in the application
 */
class MultiBookTest : GnuAndroidTest() {
    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    @Rule
    @JvmField
    val activityRule = IntentsTestRule(AccountsActivity::class.java)

    @Test
    fun shouldOpenBookManager() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.book_name))
            .check(matches(isDisplayed())).perform(click())

        onView(withText(R.string.menu_manage_books)).perform(click())

        Intents.intended(IntentMatchers.hasComponent(PreferenceActivity::class.java.name))
    }

    fun testLoadBookFromBookManager() {
        val book = Book()
        book.displayName = "Launch Codes"
        BooksDbAdapter.getInstance().addRecord(book)

        shouldOpenBookManager()
        onView(withText(book.displayName)).perform(click())

        assertThat(GnuCashApplication.getActiveBookUID()).isEqualTo(book.uid)
    }

    @Test
    fun creatingNewAccounts_shouldCreatedNewBook() {
        val bookCount = booksDbAdapter.recordsCount
        assertThat(bookCount).isOne()

        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.drawer_layout)).perform(swipeUp())
        onView(withText(R.string.title_settings)).perform(click())

        Intents.intended(IntentMatchers.hasComponent(PreferenceActivity::class.java.name))

        onView(withText(R.string.header_account_settings)).perform(click())
        onView(withText(R.string.title_create_default_accounts)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())

        /* TODO: 18.05.2016 wait for import to finish instead */
        sleep(2000) //give import time to finish

        assertThat(booksDbAdapter.recordsCount).isEqualTo(bookCount + 1)

        /* TODO: 25.08.2016 Delete all books before the start of this test */
        val activeBook = booksDbAdapter.getRecord(booksDbAdapter.activeBookUID)
        val name = context.getString(R.string.book_default_name, bookCount + 1)
        assertThat(activeBook.displayName).isEqualTo(name)
    }

    @Test
    fun testCreateNewBook() {
        val bookCount = booksDbAdapter.recordsCount

        shouldOpenBookManager()

        onView(withId(R.id.menu_create))
            .check(matches(isDisplayed()))
            .perform(click()) // select the accounts template

        onView(withText("Common Accounts"))
            .check(matches(isDisplayed()))
            .perform(click()) // create book from the accounts template

        assertThat(booksDbAdapter.recordsCount).isEqualTo(bookCount + 1)
    }

    //TODO: Finish implementation of this test
    fun testDeleteBook() {
        val bookCount = booksDbAdapter.recordsCount

        val book = Book()
        val displayName = "To Be Deleted"
        book.displayName = displayName
        booksDbAdapter.addRecord(book)

        assertThat(booksDbAdapter.recordsCount).isEqualTo(bookCount + 1)

        shouldOpenBookManager()

        onView(
            allOf(
                withParent(hasDescendant(withText(displayName))),
                withId(R.id.options_menu)
            )
        ).perform(click())

        onView(withText(R.string.menu_delete)).perform(click())
        onView(withText(R.string.btn_delete_book)).perform(click())

        assertThat(booksDbAdapter.recordsCount).isEqualTo(bookCount)
    }

    companion object {
        private lateinit var booksDbAdapter: BooksDbAdapter

        @ClassRule
        @JvmField
        val disableAnimationsRule = DisableAnimationsRule()

        @BeforeClass
        @JvmStatic
        fun prepTestCase() {
            preventFirstRunDialogs()
            booksDbAdapter = BooksDbAdapter.getInstance()
        }
    }
}
