package org.gnucash.android.util

import junit.framework.TestCase.fail
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.importer.GncXmlImporter
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class BackupManagerTest : GnuCashTest() {
    private lateinit var booksDbAdapter: BooksDbAdapter

    @Before
    fun setUp() {
        booksDbAdapter = BooksDbAdapter.getInstance()
        booksDbAdapter.deleteAllRecords()
        assertThat(booksDbAdapter.recordsCount).isZero()
    }

    @Test
    fun backupAllBooks() {
        val activeBookUID = createNewBookWithDefaultAccounts()
        BookUtils.activateBook(activeBookUID)
        createNewBookWithDefaultAccounts()
        assertThat(booksDbAdapter.recordsCount).isEqualTo(2)

        BackupManager.backupAllBooks()

        for (bookUID in booksDbAdapter.allBookUIDs) {
            assertThat(BackupManager.getBackupList(context, bookUID).size).isOne()
        }
    }

    @Test
    fun backupList() {
        val bookUID = createNewBookWithDefaultAccounts()
        BookUtils.activateBook(bookUID)

        assertThat(BackupManager.backupActiveBook()).isTrue()
        Thread.sleep(1000) // FIXME: Use Mockito to get a different date in Exporter.buildExportFilename
        assertThat(BackupManager.backupActiveBook()).isTrue()

        assertThat(BackupManager.getBackupList(context, bookUID).size).isEqualTo(2)
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
        try {
            return GncXmlImporter.parse(
                context,
                context.resources.openRawResource(R.raw.default_accounts)
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        fail("Could not create default accounts")
        return ""
    }
}