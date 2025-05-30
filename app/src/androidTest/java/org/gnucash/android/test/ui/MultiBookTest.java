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
package org.gnucash.android.test.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;

import android.Manifest;
import android.content.Context;

import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.rule.GrantPermissionRule;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.model.Book;
import org.gnucash.android.test.ui.util.DisableAnimationsRule;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test support for multiple books in the application
 */
public class MultiBookTest extends GnuAndroidTest {

    private static BooksDbAdapter mBooksDbAdapter;

    @Rule
    public GrantPermissionRule animationPermissionsRule = GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE);

    @ClassRule
    public static DisableAnimationsRule disableAnimationsRule = new DisableAnimationsRule();

    @Rule
    public IntentsTestRule<AccountsActivity> mActivityRule = new IntentsTestRule<>(AccountsActivity.class);

    @BeforeClass
    public static void prepTestCase() {
        Context context = GnuCashApplication.getAppContext();
        preventFirstRunDialogs(context);
        mBooksDbAdapter = BooksDbAdapter.getInstance();
    }

    @Test
    public void shouldOpenBookManager() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.book_name)).check(matches(isDisplayed())).perform(click());

        onView(withText(R.string.menu_manage_books)).perform(click());

        Intents.intended(hasComponent(PreferenceActivity.class.getName()));
    }

    public void testLoadBookFromBookManager() {
        Book book = new Book();
        book.setDisplayName("Launch Codes");
        BooksDbAdapter.getInstance().addRecord(book);

        shouldOpenBookManager();
        onView(withText(book.getDisplayName())).perform(click());

        assertThat(GnuCashApplication.getActiveBookUID()).isEqualTo(book.getUID());
    }

    @Test
    public void creatingNewAccounts_shouldCreatedNewBook() {
        Context context = GnuCashApplication.getAppContext();
        final long bookCount = mBooksDbAdapter.getRecordsCount();
        assertThat(bookCount).isOne();

        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.drawer_layout)).perform(swipeUp());
        onView(withText(R.string.title_settings)).perform(click());

        Intents.intended(hasComponent(PreferenceActivity.class.getName()));

        onView(withText(R.string.header_account_settings)).perform(click());
        onView(withText(R.string.title_create_default_accounts)).perform(click());
        onView(withId(android.R.id.button1)).perform(click());

        //// TODO: 18.05.2016 wait for import to finish instead
        sleep(2000); //give import time to finish

        assertThat(mBooksDbAdapter.getRecordsCount()).isEqualTo(bookCount + 1);

        //// TODO: 25.08.2016 Delete all books before the start of this test
        Book activeBook = mBooksDbAdapter.getRecord(mBooksDbAdapter.getActiveBookUID());
        String name = context.getString(R.string.book_default_name, bookCount + 1);
        assertThat(activeBook.getDisplayName()).isEqualTo(name);
    }

    @Test
    public void testCreateNewBook() {
        long bookCount = mBooksDbAdapter.getRecordsCount();

        shouldOpenBookManager();

        onView(withId(R.id.menu_create))
            .check(matches(isDisplayed()))
            .perform(click()); // select the accounts template

        onView(withText("Common Accounts"))
            .check(matches(isDisplayed()))
            .perform(click()); // create book from the accounts template

        assertThat(mBooksDbAdapter.getRecordsCount()).isEqualTo(bookCount + 1);
    }

    //TODO: Finish implementation of this test
    public void testDeleteBook() {
        long bookCount = mBooksDbAdapter.getRecordsCount();

        Book book = new Book();
        String displayName = "To Be Deleted";
        book.setDisplayName(displayName);
        mBooksDbAdapter.addRecord(book);

        assertThat(mBooksDbAdapter.getRecordsCount()).isEqualTo(bookCount + 1);

        shouldOpenBookManager();

        onView(allOf(withParent(hasDescendant(withText(displayName))),
            withId(R.id.options_menu))).perform(click());

        onView(withText(R.string.menu_delete)).perform(click());
        onView(withText(R.string.btn_delete_book)).perform(click());

        assertThat(mBooksDbAdapter.getRecordsCount()).isEqualTo(bookCount);
    }
}
