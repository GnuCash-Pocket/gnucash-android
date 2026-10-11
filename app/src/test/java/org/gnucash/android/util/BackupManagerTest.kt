package org.gnucash.android.util

import android.net.Uri
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.NoActiveBookException
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.importer.xml.GncXmlImporter
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Before
import org.junit.Test
import java.lang.Thread.sleep

class BackupManagerTest : GnuCashTest() {
    private lateinit var booksDbAdapter: BooksDbAdapter

    @Before
    fun setUp() {
        booksDbAdapter = BooksDbAdapter.instance
        BookUtils.deleteRecords(booksDbAdapter)
        assertThat(booksDbAdapter.recordsCount).isZero()
        assertThatThrownBy { GnuCashApplication.activeBookUID }
            .isInstanceOf(NoActiveBookException::class.java)
    }

    @Test
    fun backupAllBooks() {
        val activeBookUID = createNewBookWithDefaultAccounts()
        BookUtils.activateBook(activeBookUID)
        createNewBookWithDefaultAccounts()
        assertThat(booksDbAdapter.recordsCount).isEqualTo(2)

        BackupManager.backupAllBooks()

        for (bookUID in booksDbAdapter.allBookUIDs) {
            assertThat(BackupManager.getBackupList(context, bookUID)).hasSize(1)
        }
    }

    @Test
    fun backupList() {
        val bookUID = createNewBookWithDefaultAccounts()
        BookUtils.activateBook(bookUID)

        assertThat(BackupManager.backupActiveBook()).isTrue()
        sleep(1000) // FIXME: Use Mockito to get a different date in Exporter.buildExportFilename
        assertThat(BackupManager.backupActiveBook()).isTrue()

        assertThat(BackupManager.getBackupList(context, bookUID)).hasSize(2)
    }

    @Test
    fun whenNoBackupsHaveBeenDone_shouldReturnEmptyBackupList() {
        val bookUID = createNewBookWithDefaultAccounts()
        BookUtils.activateBook(bookUID)

        assertThat(BackupManager.getBackupList(context, bookUID)).isEmpty()
    }

    /**
     * Creates a new database with default accounts
     *
     * @return The book UID for the new database
     * @throws RuntimeException if the new books could not be created
     */
    private fun createNewBookWithDefaultAccounts(): String {
        return GncXmlImporter.parse(
            context,
            Uri.EMPTY,
            context.resources.openRawResource(R.raw.default_accounts)
        )
    }
}