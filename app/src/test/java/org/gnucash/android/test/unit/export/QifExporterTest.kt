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
package org.gnucash.android.test.unit.export

import android.database.sqlite.SQLiteDatabase
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.qif.QifExporter
import org.gnucash.android.export.qif.QifHelper
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Book
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.util.TimestampHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.zip.ZipFile

class QifExporterTest : BookHelperTest() {
    private var bookUID: String? = null
    private lateinit var db: SQLiteDatabase

    @Before
    override fun setUp() {
        super.setUp()
        val testBook = Book("testRootAccountUID")
        booksDbAdapter.addRecord(testBook)
        bookUID = testBook.uid
        val databaseHelper = DatabaseHelper(context, testBook.uid)
        db = databaseHelper.writableDatabase
    }

    @After
    override fun tearDown() {
        val booksDbAdapter = BooksDbAdapter.getInstance()
        booksDbAdapter.deleteBook(context, bookUID!!)
        db.close()
    }

    /**
     * When there aren't new or modified transactions, the QIF exporter
     * shouldn't create any file.
     */
    @Test
    fun testWithNoTransactionsToExport_shouldNotCreateAnyFile() {
        val exportParameters = ExportParams(ExportFormat.QIF)
        exportParameters.exportStartTime = TimestampHelper.getTimestampFromEpochZero()
        exportParameters.exportTarget = ExportParams.ExportTarget.SD_CARD
        exportParameters.setDeleteTransactionsAfterExport(false)
        val exporter = QifExporter(context, exportParameters, bookUID!!)
        val exportedFile = exporter.export()
        assertThat(exportedFile).isNull()
    }

    /**
     * Test that QIF files are generated
     */
    @Test
    fun testGenerateQIFExport() {
        val accountsDbAdapter = AccountsDbAdapter(db)

        val account = Account("Basic Account")
        val transaction = Transaction("One transaction")
        transaction.addSplit(Split(createZeroInstance("EUR"), account.uid))
        account.addTransaction(transaction)

        accountsDbAdapter.addRecord(account)

        val exportParameters = ExportParams(ExportFormat.QIF)
        exportParameters.exportStartTime = TimestampHelper.getTimestampFromEpochZero()
        exportParameters.exportTarget = ExportParams.ExportTarget.SD_CARD
        exportParameters.setDeleteTransactionsAfterExport(false)

        val exporter = QifExporter(context, exportParameters, bookUID!!)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path)
        assertThat(file).exists().hasExtension("qif")
        assertThat(file.length()).isGreaterThan(0L)
        file.delete()
    }

    /**
     * Test that when more than one currency is in use, a zip with multiple QIF files
     * will be generated
     */
    // @Test Fails randomly. Sometimes it doesn't split the QIF.
    @Throws(IOException::class)
    fun multiCurrencyTransactions_shouldResultInMultipleZippedQifFiles() {
        val accountsDbAdapter = AccountsDbAdapter(db)

        val account = Account("Basic Account", getInstance("EUR"))
        val transaction = Transaction("One transaction")
        transaction.addSplit(Split(createZeroInstance("EUR"), account.uid))
        account.addTransaction(transaction)
        accountsDbAdapter.addRecord(account)

        val foreignAccount = Account("US Konto", getInstance("USD"))
        val multiCurr = Transaction("multi-currency")
        val split1 = Split(Money("12", "USD"), Money("15", "EUR"), foreignAccount.uid)
        val split2 = split1.createPair(account.uid)
        multiCurr.addSplit(split1)
        multiCurr.addSplit(split2)
        foreignAccount.addTransaction(multiCurr)

        accountsDbAdapter.addRecord(foreignAccount)

        val exportParameters = ExportParams(ExportFormat.QIF)
        exportParameters.exportStartTime = TimestampHelper.getTimestampFromEpochZero()
        exportParameters.exportTarget = ExportParams.ExportTarget.SD_CARD
        exportParameters.setDeleteTransactionsAfterExport(false)

        val exporter = QifExporter(context, exportParameters, bookUID!!)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path)
        assertThat(file).exists().hasExtension("zip")
        assertThat(ZipFile(file).size()).isEqualTo(2)
        file.delete()
    }

    /**
     * Test that the memo and description fields of transactions are exported.
     */
    @Test
    fun memoAndDescription_shouldBeExported() {
        val expectedDescription = "my description"
        val expectedMemo = "my memo"
        val expectedAccountName = "Basic Account"

        val accountsDbAdapter = AccountsDbAdapter(db)

        val account = Account(expectedAccountName)
        val transaction = Transaction("One transaction")
        transaction.addSplit(Split(Money("123.45", "EUR"), account.uid))
        transaction.description = expectedDescription
        transaction.note = expectedMemo
        account.addTransaction(transaction)

        accountsDbAdapter.addRecord(account)

        val exportParameters = ExportParams(ExportFormat.QIF)
        exportParameters.exportStartTime = TimestampHelper.getTimestampFromEpochZero()
        exportParameters.exportTarget = ExportParams.ExportTarget.SD_CARD
        exportParameters.setDeleteTransactionsAfterExport(false)

        val exporter = QifExporter(context, exportParameters, bookUID!!)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path)
        assertThat(file).exists().hasExtension("qif")
        val fileContent = readFileContent(file)
        assertThat(fileContent).isNotEmpty()
        val lines = fileContent.split(QifHelper.NEW_LINE.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        assertThat(lines).isNotEmpty()
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_SECTION)
        assertThat(lines[1]).isEqualTo(QifHelper.ACCOUNT_NAME_PREFIX + expectedAccountName)
        assertThat(lines[2]).isEqualTo(QifHelper.TYPE_PREFIX + "Cash")
        assertThat(lines[3]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        assertThat(lines[4]).isEqualTo(QifHelper.TRANSACTION_TYPE_PREFIX + "Cash")
        assertThat(lines[5]).startsWith(QifHelper.DATE_PREFIX)
        assertThat(lines[6]).isEqualTo(QifHelper.CATEGORY_PREFIX + "[" + expectedAccountName + "]")
        assertThat(lines[7]).isEqualTo(QifHelper.PAYEE_PREFIX + expectedDescription)
        assertThat(lines[8]).isEqualTo(QifHelper.MEMO_PREFIX + expectedMemo)
        assertThat(lines[9]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[Imbalance-USD]")
        assertThat(lines[10]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "-123.45")
        assertThat(lines[11]).isEqualTo(QifHelper.TOTAL_AMOUNT_PREFIX + "-123.45")
        assertThat(lines[12]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        file.delete()
    }

    /**
     * Tests exporting a simple transaction with default splits.
     */
    @Test
    fun simpleTransactionExport() {
        val bookUID = importGnuCashXml("simpleTransactionImport.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(transactionsDbAdapter!!.recordsCount).isEqualTo(1)

        val transaction = transactionsDbAdapter!!.getRecord("b33c8a6160494417558fd143731fc26a")
        assertThat(transaction.splits.size).isEqualTo(2)

        val exportParameters = ExportParams(ExportFormat.QIF)
        exportParameters.exportStartTime = TimestampHelper.getTimestampFromEpochZero()
        exportParameters.exportTarget = ExportParams.ExportTarget.SD_CARD
        exportParameters.setDeleteTransactionsAfterExport(false)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path)
        assertThat(file).exists().hasExtension("qif")
        val fileContent = readFileContent(file)
        assertThat(fileContent).isNotEmpty()
        val lines = fileContent.split(QifHelper.NEW_LINE.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        assertThat(lines).isNotEmpty()
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_SECTION)
        assertThat(lines[1]).isEqualTo(QifHelper.ACCOUNT_NAME_PREFIX + "Expenses:Dining")
        assertThat(lines[2]).isEqualTo(QifHelper.TYPE_PREFIX + "Cash")
        assertThat(lines[3]).isEqualTo(QifHelper.ACCOUNT_DESCRIPTION_PREFIX + "Dining")
        assertThat(lines[4]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        assertThat(lines[5]).isEqualTo(QifHelper.TRANSACTION_TYPE_PREFIX + "Cash")
        assertThat(lines[6]).isEqualTo(QifHelper.DATE_PREFIX + "2016/8/23")
        assertThat(lines[7]).isEqualTo(QifHelper.CATEGORY_PREFIX + "[Expenses:Dining]")
        assertThat(lines[8]).isEqualTo(QifHelper.PAYEE_PREFIX + "Kahuna Burger")
        assertThat(lines[9]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[Assets:Cash in Wallet]")
        assertThat(lines[10]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "10.00")
        assertThat(lines[11]).isEqualTo(QifHelper.TOTAL_AMOUNT_PREFIX + "10.00")
        assertThat(lines[12]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        file.delete()
    }

    /**
     * Tests exporting a transaction with non-default splits.
     */
    @Test
    fun transactionWithNonDefaultSplitsImport() {
        val bookUID = importGnuCashXml("transactionWithNonDefaultSplitsImport.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(transactionsDbAdapter!!.recordsCount).isEqualTo(1)

        val transaction = transactionsDbAdapter!!.getRecord("042ff745a80e94e6237fb0549f6d32ae")

        // Ensure it's the correct one
        assertThat(transaction.description).isEqualTo("Tandoori Mahal")

        // Check splits
        assertThat(transaction.splits.size).isEqualTo(3)

        val exportParameters = ExportParams(ExportFormat.QIF)
        exportParameters.exportStartTime = TimestampHelper.getTimestampFromEpochZero()
        exportParameters.exportTarget = ExportParams.ExportTarget.SD_CARD
        exportParameters.setDeleteTransactionsAfterExport(false)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path)
        assertThat(file).exists().hasExtension("qif")
        val fileContent = readFileContent(file)
        assertThat(fileContent).isNotEmpty()
        val lines = fileContent.split(QifHelper.NEW_LINE.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        assertThat(lines).isNotEmpty()
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_SECTION)
        assertThat(lines[1]).isEqualTo(QifHelper.ACCOUNT_NAME_PREFIX + "Expenses:Dining")
        assertThat(lines[2]).isEqualTo(QifHelper.TYPE_PREFIX + "Cash")
        assertThat(lines[3]).isEqualTo(QifHelper.ACCOUNT_DESCRIPTION_PREFIX + "Dining")
        assertThat(lines[4]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        assertThat(lines[5]).isEqualTo(QifHelper.TRANSACTION_TYPE_PREFIX + "Cash")
        assertThat(lines[6]).isEqualTo(QifHelper.DATE_PREFIX + "2016/9/18")
        assertThat(lines[7]).isEqualTo(QifHelper.CATEGORY_PREFIX + "[Expenses:Dining]")
        assertThat(lines[8]).isEqualTo(QifHelper.PAYEE_PREFIX + "Tandoori Mahal")
        assertThat(lines[9]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[Assets:Bank]")
        assertThat(lines[10]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "45.00")
        assertThat(lines[11]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[Assets:Cash in Wallet]")
        assertThat(lines[12]).isEqualTo(QifHelper.SPLIT_MEMO_PREFIX + "tip")
        assertThat(lines[13]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "5.00")
        assertThat(lines[14]).isEqualTo(QifHelper.TOTAL_AMOUNT_PREFIX + "50.00")
        assertThat(lines[15]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        file.delete()
    }

    /**
     * Test that the memo and description fields of transactions are exported.
     */
    @Test
    @Throws(IOException::class)
    fun amountAndSplit_shouldBeExported() {
        val expectedDescription = "my description"
        val expectedMemo = "my memo"
        val expectedAccountName1 = "Basic Account"
        val expectedAccountName2 = "Cash in Wallet"

        val accountsDbAdapter = AccountsDbAdapter(db)
        val account1 = Account(expectedAccountName1, Commodity.EUR)
        account1.accountType = AccountType.EXPENSE
        account1.setUID("account-001")
        accountsDbAdapter.addRecord(account1)
        val account2 = Account(expectedAccountName2, Commodity.EUR)
        account2.accountType = AccountType.CASH
        account2.setUID("account-002")
        accountsDbAdapter.addRecord(account2)

        val transactionsDbAdapter = TransactionsDbAdapter(db)
        val transaction = Transaction("One transaction")
        val split1 = Split(Money(123.45, Commodity.EUR), account1.uid)
        val split2 = split1.createPair(account2.uid)
        split2.accountUID = account2.uid
        transaction.addSplit(split1)
        transaction.addSplit(split2)
        transaction.description = expectedDescription
        transaction.note = expectedMemo
        transactionsDbAdapter.addRecord(transaction)

        val exportParameters = ExportParams(ExportFormat.QIF)
        exportParameters.exportStartTime = TimestampHelper.getTimestampFromEpochZero()
        exportParameters.exportTarget = ExportParams.ExportTarget.SD_CARD
        exportParameters.setDeleteTransactionsAfterExport(false)

        val exporter = QifExporter(context, exportParameters, bookUID!!)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path)
        assertThat(file).exists().hasExtension("qif")
        val fileContent = readFileContent(file)
        assertThat(fileContent).isNotEmpty()
        val lines = fileContent.split(QifHelper.NEW_LINE.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        assertThat(lines).isNotEmpty()
        assertThat(lines[0]).isEqualTo(QifHelper.ACCOUNT_SECTION)
        assertThat(lines[1]).isEqualTo(QifHelper.ACCOUNT_NAME_PREFIX + expectedAccountName1)
        assertThat(lines[2]).isEqualTo(QifHelper.TYPE_PREFIX + "Cash")
        assertThat(lines[3]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        assertThat(lines[4]).isEqualTo(QifHelper.TRANSACTION_TYPE_PREFIX + "Cash")
        assertThat(lines[5]).startsWith(QifHelper.DATE_PREFIX)
        assertThat(lines[6]).isEqualTo(QifHelper.CATEGORY_PREFIX + "[" + expectedAccountName1 + "]")
        assertThat(lines[7]).isEqualTo(QifHelper.PAYEE_PREFIX + expectedDescription)
        assertThat(lines[8]).isEqualTo(QifHelper.MEMO_PREFIX + expectedMemo)
        assertThat(lines[9]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[" + expectedAccountName2 + "]")
        assertThat(lines[10]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "-123.45")
        assertThat(lines[11]).isEqualTo(QifHelper.TOTAL_AMOUNT_PREFIX + "-123.45")
        assertThat(lines[12]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        file.delete()
    }

    @Throws(IOException::class)
    fun readFileContent(file: File?): String {
        val fileContentsBuilder = StringBuilder()
        val reader = BufferedReader(FileReader(file))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            fileContentsBuilder.append(line).append('\n')
        }

        return fileContentsBuilder.toString()
    }
}