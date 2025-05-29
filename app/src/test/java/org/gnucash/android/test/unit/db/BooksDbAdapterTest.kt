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
package org.gnucash.android.test.unit.db

import junit.framework.TestCase.fail
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter.NoActiveBookFoundException
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.importer.GncXmlImporter
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.Book
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Before
import org.junit.Test
import org.xml.sax.SAXException
import timber.log.Timber
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * Test the book database adapter
 */
class BooksDbAdapterTest : GnuCashTest() {
    private lateinit var booksDbAdapter: BooksDbAdapter

    @Before
    fun setUp() {
        booksDbAdapter = BooksDbAdapter.getInstance()
        assertThat(booksDbAdapter.recordsCount).isEqualTo(1) //there is always a default book after app start
        assertThat(booksDbAdapter.activeBookUID).isNotNull()

        booksDbAdapter.deleteAllRecords()
        assertThat(booksDbAdapter.recordsCount).isZero()
    }

    @Test
    fun addBook() {
        val book = Book(generateUID())
        booksDbAdapter.addRecord(book, DatabaseAdapter.UpdateMethod.insert)

        assertThat(booksDbAdapter.recordsCount).isEqualTo(1)
        assertThat(booksDbAdapter.getRecord(book.uid).displayName).isEqualTo("Book 1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun savingBook_requiresRootAccountGUID() {
        val book = Book()
        booksDbAdapter.addRecord(book)
    }

    @Test
    fun deleteBook() {
        val book = Book()
        book.rootAccountUID = generateUID()
        booksDbAdapter.addRecord(book)

        booksDbAdapter.deleteRecord(book.uid)

        assertThat(booksDbAdapter.recordsCount).isZero()
    }

    @Test
    fun setBookActive() {
        val book1 = Book(generateUID())
        val book2 = Book(generateUID())

        booksDbAdapter.addRecord(book1)
        booksDbAdapter.addRecord(book2)

        booksDbAdapter.setActive(book1.uid)

        assertThat(booksDbAdapter.activeBookUID).isEqualTo(book1.uid)

        booksDbAdapter.setActive(book2.uid)
        assertThat(booksDbAdapter.isActive(book2.uid)).isTrue()
        //setting book2 as active should disable book1 as active
        val book = booksDbAdapter.getRecord(book1.uid)
        assertThat(book.isActive).isFalse()
    }

    /**
     * Test that the generated display name has an ordinal greater than the number of
     * book records in the database
     */
    @Test
    fun testGeneratedDisplayName() {
        val book1 = Book(generateUID())
        val book2 = Book(generateUID())

        booksDbAdapter.addRecord(book1)
        booksDbAdapter.addRecord(book2)

        assertThat(booksDbAdapter.generateDefaultBookName()).isEqualTo("Book 3")
    }

    /**
     * Test that deleting a book record also deletes the book database
     */
    @Test
    fun deletingBook_shouldDeleteDbFile() {
        val bookUID = createNewBookWithDefaultAccounts()
        val dbPath = GnuCashApplication.getAppContext().getDatabasePath(bookUID)
        assertThat(dbPath).exists()
        val booksDbAdapter = BooksDbAdapter.getInstance()
        assertThat(booksDbAdapter.getRecord(bookUID)).isNotNull()

        val booksCount = booksDbAdapter.recordsCount
        booksDbAdapter.deleteBook(context, bookUID)
        assertThat(dbPath).doesNotExist()
        assertThat(booksDbAdapter.recordsCount).isEqualTo(booksCount - 1)
    }

    /**
     * Test that book names never conflict and that the ordinal attached to the book name is
     * increased irrespective of the order in which books are added to and deleted from the db
     */
    @Test
    fun testGeneratedDisplayNames_shouldBeUnique() {
        val name1 = context.getString(R.string.book_default_name, 1)
        val name2 = context.getString(R.string.book_default_name, 2)
        val name3 = context.getString(R.string.book_default_name, 3)
        val name4 = context.getString(R.string.book_default_name, 4)

        val book1 = Book(generateUID())
        val book2 = Book(generateUID())
        val book3 = Book(generateUID())

        booksDbAdapter.addRecord(book1)
        assertThat(book1.id).isNotZero()
        assertThat(book1.displayName).isEqualTo(name1)
        booksDbAdapter.addRecord(book2)
        assertThat(book2.id).isNotZero()
        assertThat(book2.displayName).isEqualTo(name2)
        booksDbAdapter.addRecord(book3)
        assertThat(book3.id).isNotZero()
        assertThat(book3.displayName).isEqualTo(name3)

        assertThat(booksDbAdapter.recordsCount).isEqualTo(3L)

        booksDbAdapter.deleteRecord(book2.uid)
        assertThat(booksDbAdapter.recordsCount).isEqualTo(2L)

        val generatedName = booksDbAdapter.generateDefaultBookName()
        assertThat(generatedName).isEqualTo(name4)
    }

    @Test
    fun recoverFromNoActiveBookFound() {
        val book1 = Book(generateUID())
        book1.isActive = false
        booksDbAdapter.addRecord(book1)

        val book2 = Book(generateUID())
        book2.isActive = false
        booksDbAdapter.addRecord(book2)

        try {
            booksDbAdapter.activeBookUID
            fail("There shouldn't be any active book.")
        } catch (e: NoActiveBookFoundException) {
            booksDbAdapter.fixBooksDatabase()
        }

        assertThat(booksDbAdapter.activeBookUID).isEqualTo(book1.uid)
    }

    /**
     * Tests the recovery from an empty books database.
     */
    @Test
    fun recoverFromEmptyDatabase() {
        createNewBookWithDefaultAccounts()
        booksDbAdapter.deleteAllRecords()
        assertThat(booksDbAdapter.recordsCount).isZero()

        booksDbAdapter.fixBooksDatabase()

        // Should've recovered the one from setUp() plus the one created above
        assertThat(booksDbAdapter.recordsCount).isEqualTo(2)
        booksDbAdapter.activeBookUID // should not throw exception
    }

    /**
     * Creates a new database with default accounts
     *
     * @return The book UID for the new database
     * @throws RuntimeException if the new books could not be created
     */
    private fun createNewBookWithDefaultAccounts(): String {
        try {
            return GncXmlImporter.parse(
                context,
                context.resources.openRawResource(R.raw.default_accounts)
            )
        } catch (e: ParserConfigurationException) {
            Timber.e(e)
            throw RuntimeException("Could not create default accounts")
        } catch (e: SAXException) {
            Timber.e(e)
            throw RuntimeException("Could not create default accounts")
        } catch (e: IOException) {
            Timber.e(e)
            throw RuntimeException("Could not create default accounts")
        }
    }
}
