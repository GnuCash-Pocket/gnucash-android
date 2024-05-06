/*
 * Copyright (c) 2016 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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
package org.gnucash.android.test.unit.export;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.qif.QifExporter;
import org.gnucash.android.export.qif.QifHelper;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.test.unit.BookHelperTest;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.gnucash.android.util.TimestampHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

@RunWith(RobolectricTestRunner.class)
//package is required so that resources can be found in dev mode
@Config(sdk = 21, shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class QifExporterTest extends BookHelperTest {

    private String mBookUID;
    private SQLiteDatabase mDb;

    @Before
    public void setUp() throws Exception {
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        Book testBook = new Book("testRootAccountUID");
        booksDbAdapter.addRecord(testBook);
        mBookUID = testBook.getUID();
        DatabaseHelper databaseHelper =
            new DatabaseHelper(GnuCashApplication.getAppContext(), testBook.getUID());
        mDb = databaseHelper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        booksDbAdapter.deleteBook(mBookUID);
        mDb.close();
    }

    /**
     * When there aren't new or modified transactions, the QIF exporter
     * shouldn't create any file.
     */
    @Test
    public void testWithNoTransactionsToExport_shouldNotCreateAnyFile() {
        Context context = GnuCashApplication.getAppContext();
        ExportParams exportParameters = new ExportParams(ExportFormat.QIF);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);
        QifExporter exporter = new QifExporter(context, exportParameters, mBookUID);
        assertThat(exporter.generateExport()).isEmpty();
    }

    /**
     * Test that QIF files are generated
     */
    @Test
    public void testGenerateQIFExport() {
        Context context = GnuCashApplication.getAppContext();
        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(mDb);

        Account account = new Account("Basic Account");
        Transaction transaction = new Transaction("One transaction");
        transaction.addSplit(new Split(Money.createZeroInstance("EUR"), account.getUID()));
        account.addTransaction(transaction);

        accountsDbAdapter.addRecord(account);

        ExportParams exportParameters = new ExportParams(ExportFormat.QIF);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        QifExporter exporter = new QifExporter(context, exportParameters, mBookUID);
        List<String> exportedFiles = exporter.generateExport();

        assertThat(exportedFiles).hasSize(1);
        File file = new File(exportedFiles.get(0));
        assertThat(file).exists().hasExtension("qif");
        assertThat(file.length()).isGreaterThan(0L);
        file.delete();
    }

    /**
     * Test that when more than one currency is in use, a zip with multiple QIF files
     * will be generated
     */
    // @Test Fails randomly. Sometimes it doesn't split the QIF.
    public void multiCurrencyTransactions_shouldResultInMultipleZippedQifFiles() throws IOException {
        Context context = GnuCashApplication.getAppContext();
        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(mDb);

        Account account = new Account("Basic Account", Commodity.getInstance("EUR"));
        Transaction transaction = new Transaction("One transaction");
        transaction.addSplit(new Split(Money.createZeroInstance("EUR"), account.getUID()));
        account.addTransaction(transaction);
        accountsDbAdapter.addRecord(account);

        Account foreignAccount = new Account("US Konto", Commodity.getInstance("USD"));
        Transaction multiCurr = new Transaction("multi-currency");
        Split split1 = new Split(new Money("12", "USD"), new Money("15", "EUR"), foreignAccount.getUID());
        Split split2 = split1.createPair(account.getUID());
        multiCurr.addSplit(split1);
        multiCurr.addSplit(split2);
        foreignAccount.addTransaction(multiCurr);

        accountsDbAdapter.addRecord(foreignAccount);

        ExportParams exportParameters = new ExportParams(ExportFormat.QIF);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        QifExporter exporter = new QifExporter(context, exportParameters, mBookUID);
        List<String> exportedFiles = exporter.generateExport();

        assertThat(exportedFiles).hasSize(1);
        File file = new File(exportedFiles.get(0));
        assertThat(file).exists().hasExtension("zip");
        assertThat(new ZipFile(file).size()).isEqualTo(2);
        file.delete();
    }

    /**
     * Test that the memo and description fields of transactions are exported.
     */
    @Test
    public void memoAndDescription_shouldBeExported() throws Exception {
        Context context = GnuCashApplication.getAppContext();
        String expectedDescription = "my description";
        String expectedMemo = "my memo";
        String expectedAccountName = "Basic Account";

        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(mDb);

        Account account = new Account(expectedAccountName);
        Transaction transaction = new Transaction("One transaction");
        transaction.addSplit(new Split(new Money("123.45", "EUR"), account.getUID()));
        transaction.setDescription(expectedDescription);
        transaction.setNote(expectedMemo);
        account.addTransaction(transaction);

        accountsDbAdapter.addRecord(account);

        ExportParams exportParameters = new ExportParams(ExportFormat.QIF);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        QifExporter exporter = new QifExporter(context, exportParameters, mBookUID);
        List<String> exportedFiles = exporter.generateExport();

        assertThat(exportedFiles).hasSize(1);
        File file = new File(exportedFiles.get(0));
        String fileContent = readFileContent(file);
        assertThat(file).exists().hasExtension("qif");
        String[] lines = fileContent.split(QifHelper.NEW_LINE);
        assertThat(lines).isNotEmpty();
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_HEADER);
        assertThat(lines).contains(QifHelper.ACCOUNT_NAME_PREFIX + expectedAccountName);
        assertThat(lines).contains(QifHelper.PAYEE_PREFIX + expectedDescription);
        assertThat(lines).contains(QifHelper.MEMO_PREFIX + expectedMemo);
        assertThat(lines).contains(QifHelper.SPLIT_CATEGORY_PREFIX + "Imbalance-USD");
        assertThat(lines).contains(QifHelper.SPLIT_AMOUNT_PREFIX + "-123.45");
        assertThat(lines).contains(QifHelper.ENTRY_TERMINATOR);
        file.delete();
    }

    /**
     * Tests exporting a simple transaction with default splits.
     */
    @Test
    public void simpleTransactionExport() throws Exception {
        Context context = GnuCashApplication.getAppContext();
        String bookUID = importGnuCashXml("simpleTransactionImport.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("b33c8a6160494417558fd143731fc26a");
        assertThat(transaction.getSplits().size()).isEqualTo(2);

        ExportParams exportParameters = new ExportParams(ExportFormat.QIF);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        QifExporter qifExporter = new QifExporter(context, exportParameters, bookUID);
        List<String> exportedFiles = qifExporter.generateExport();

        assertThat(exportedFiles).hasSize(1);
        File file = new File(exportedFiles.get(0));
        String fileContent = readFileContent(file);
        assertThat(file).exists().hasExtension("qif");
        String[] lines = fileContent.split(QifHelper.NEW_LINE);
        assertThat(lines).isNotEmpty();
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_HEADER);
        assertThat(lines).contains(QifHelper.ACCOUNT_NAME_PREFIX + "Expenses:Dining");
        assertThat(lines).contains(QifHelper.PAYEE_PREFIX + "Kahuna Burger");
        assertThat(lines).contains(QifHelper.SPLIT_CATEGORY_PREFIX + "Assets:Cash in Wallet");
        assertThat(lines).contains(QifHelper.SPLIT_AMOUNT_PREFIX + "10.00");
        assertThat(lines).contains(QifHelper.DATE_PREFIX + "2016/8/23");
        assertThat(lines).contains(QifHelper.ENTRY_TERMINATOR);
        file.delete();
    }

    /**
     * Tests exporting a transaction with non-default splits.
     */
    @Test
    public void transactionWithNonDefaultSplitsImport() throws Exception {
        Context context = GnuCashApplication.getAppContext();
        String bookUID = importGnuCashXml("transactionWithNonDefaultSplitsImport.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("042ff745a80e94e6237fb0549f6d32ae");

        // Ensure it's the correct one
        assertThat(transaction.getDescription()).isEqualTo("Tandoori Mahal");

        // Check splits
        assertThat(transaction.getSplits().size()).isEqualTo(3);

        ExportParams exportParameters = new ExportParams(ExportFormat.QIF);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        QifExporter qifExporter = new QifExporter(context, exportParameters, bookUID);
        List<String> exportedFiles = qifExporter.generateExport();

        assertThat(exportedFiles).hasSize(1);
        File file = new File(exportedFiles.get(0));
        String fileContent = readFileContent(file);
        assertThat(file).exists().hasExtension("qif");
        String[] lines = fileContent.split(QifHelper.NEW_LINE);
        assertThat(lines).isNotEmpty();
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_HEADER);
        assertThat(lines).contains(QifHelper.ACCOUNT_NAME_PREFIX + "Expenses:Dining");
        assertThat(lines).contains(QifHelper.PAYEE_PREFIX + "Tandoori Mahal");
        assertThat(lines).contains(QifHelper.DATE_PREFIX + "2016/9/18");
        assertThat(lines).contains(QifHelper.SPLIT_CATEGORY_PREFIX + "Assets:Bank");
        assertThat(lines).contains(QifHelper.SPLIT_AMOUNT_PREFIX + "45.00");
        assertThat(lines).contains(QifHelper.SPLIT_CATEGORY_PREFIX + "Assets:Cash in Wallet");
        assertThat(lines).contains(QifHelper.SPLIT_AMOUNT_PREFIX + "5.00");
        assertThat(lines).contains(QifHelper.SPLIT_MEMO_PREFIX + "tip");
        assertThat(lines).contains(QifHelper.ENTRY_TERMINATOR);
        file.delete();
    }

    /**
     * Test that the memo and description fields of transactions are exported.
     */
    @Test
    public void amountAndSplit_shouldBeExported() throws IOException {
        Context context = GnuCashApplication.getAppContext();
        String expectedDescription = "my description";
        String expectedMemo = "my memo";
        String expectedAccountName1 = "Basic Account";
        String expectedAccountName2 = "Cash in Wallet";

        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(mDb);
        Account account1 = new Account(expectedAccountName1, Commodity.EUR);
        account1.setAccountType(AccountType.EXPENSE);
        accountsDbAdapter.addRecord(account1);
        Account account2 = new Account(expectedAccountName2, Commodity.EUR);
        account2.setAccountType(AccountType.CASH);
        accountsDbAdapter.addRecord(account2);

        SplitsDbAdapter splitsDbAdapter = new SplitsDbAdapter(mDb);
        TransactionsDbAdapter transactionsDbAdapter = new TransactionsDbAdapter(mDb, splitsDbAdapter);
        Transaction transaction = new Transaction("One transaction");
        Split split1 = new Split(new Money(123.45, Commodity.EUR), account1.getUID());
        Split split2 = split1.createPair(account2.getUID());
        split2.setAccountUID(account2.getUID());
        transaction.addSplit(split1);
        transaction.addSplit(split2);
        transaction.setDescription(expectedDescription);
        transaction.setNote(expectedMemo);
        transactionsDbAdapter.addRecord(transaction);

        ExportParams exportParameters = new ExportParams(ExportFormat.QIF);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        QifExporter qifExporter = new QifExporter(context, exportParameters, mBookUID);
        List<String> exportedFiles = qifExporter.generateExport();

        assertThat(exportedFiles).hasSize(1);
        File file = new File(exportedFiles.get(0));
        String fileContent = readFileContent(file);
        assertThat(file).exists().hasExtension("qif");
        String[] lines = fileContent.split(QifHelper.NEW_LINE);
        assertThat(lines).isNotEmpty();
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_HEADER);
        assertThat(lines).contains(QifHelper.PAYEE_PREFIX + expectedDescription);
        assertThat(lines).contains(QifHelper.MEMO_PREFIX + expectedMemo);
        if (lines[1].equals(QifHelper.ACCOUNT_NAME_PREFIX + expectedAccountName1)) {
            assertThat(lines).contains(QifHelper.ACCOUNT_NAME_PREFIX + expectedAccountName1);
            assertThat(lines).contains(QifHelper.SPLIT_CATEGORY_PREFIX + expectedAccountName2);
            assertThat(lines).contains(QifHelper.SPLIT_AMOUNT_PREFIX + "-123.45");
        } else {
            assertThat(lines).contains(QifHelper.ACCOUNT_NAME_PREFIX + expectedAccountName2);
            assertThat(lines).contains(QifHelper.SPLIT_CATEGORY_PREFIX + expectedAccountName1);
            assertThat(lines).contains(QifHelper.SPLIT_AMOUNT_PREFIX + "123.45");
        }
        assertThat(lines).contains(QifHelper.ENTRY_TERMINATOR);
        file.delete();
    }

    @NonNull
    public String readFileContent(File file) throws IOException {
        StringBuilder fileContentsBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            fileContentsBuilder.append(line).append('\n');
        }

        return fileContentsBuilder.toString();
    }
}