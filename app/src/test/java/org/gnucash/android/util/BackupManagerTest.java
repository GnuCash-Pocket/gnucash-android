package org.gnucash.android.util;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.importer.GncXmlImporter;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import timber.log.Timber;

public class BackupManagerTest extends GnuCashTest {
    private BooksDbAdapter mBooksDbAdapter;

    @Before
    public void setUp() throws Exception {
        mBooksDbAdapter = BooksDbAdapter.getInstance();
        mBooksDbAdapter.deleteAllRecords();
        assertThat(mBooksDbAdapter.getRecordsCount()).isEqualTo(0);
    }

    @Test
    public void backupAllBooks() throws Exception {
        Context context = GnuCashApplication.getAppContext();
        String activeBookUID = createNewBookWithDefaultAccounts();
        BookUtils.activateBook(activeBookUID);
        createNewBookWithDefaultAccounts();
        assertThat(mBooksDbAdapter.getRecordsCount()).isEqualTo(2);

        BackupManager.backupAllBooks();

        for (String bookUID : mBooksDbAdapter.getAllBookUIDs()) {
            assertThat(BackupManager.getBackupList(context, bookUID).size()).isEqualTo(1);
        }
    }

    @Test
    public void getBackupList() throws Exception {
        Context context = GnuCashApplication.getAppContext();
        String bookUID = createNewBookWithDefaultAccounts();
        BookUtils.activateBook(bookUID);

        assertThat(BackupManager.backupActiveBook()).isTrue();
        Thread.sleep(1000); // FIXME: Use Mockito to get a different date in Exporter.buildExportFilename
        assertThat(BackupManager.backupActiveBook()).isTrue();

        assertThat(BackupManager.getBackupList(context, bookUID).size()).isEqualTo(2);
    }

    @Test
    public void whenNoBackupsHaveBeenDone_shouldReturnEmptyBackupList() {
        Context context = GnuCashApplication.getAppContext();
        String bookUID = createNewBookWithDefaultAccounts();
        BookUtils.activateBook(bookUID);

        assertThat(BackupManager.getBackupList(context, bookUID)).isEmpty();
    }

    /**
     * Creates a new database with default accounts
     *
     * @return The book UID for the new database
     * @throws RuntimeException if the new books could not be created
     */
    private String createNewBookWithDefaultAccounts() {
        try {
            Context context = GnuCashApplication.getAppContext();
            return GncXmlImporter.parse(context, context.getResources().openRawResource(R.raw.default_accounts));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Timber.e(e);
            throw new RuntimeException("Could not create default accounts");
        }
    }
}