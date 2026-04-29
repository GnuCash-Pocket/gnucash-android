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

import androidx.core.net.toFile
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.qif.QifExporter
import org.gnucash.android.export.qif.QifHelper
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Book
import org.gnucash.android.model.Commodity
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
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class QifExporterTest : BookHelperTest() {
    private lateinit var bookUID: String

    @Before
    override fun setUp() {
        super.setUp()
        val testBook = Book("testRootAccountUID")
        booksDbAdapter.addRecord(testBook)
        bookUID = testBook.uid
        setUpDbAdapters(testBook.uid)
    }

    @After
    override fun tearDown() {
        val booksDbAdapter = BooksDbAdapter.instance
        booksDbAdapter.deleteBook(context, bookUID)
        booksDbAdapter.close()
    }

    /**
     * When there aren't new or modified transactions, the QIF exporter
     * shouldn't create any file.
     */
    @Test
    fun testWithNoTransactionsToExport_shouldNotCreateAnyFile() {
        val exportParameters = ExportParams(ExportFormat.QIF)
        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()
        assertThat(exportedFile).isNull()
    }

    /**
     * Test that QIF files are generated
     */
    @Test
    fun testGenerateQIFExport() {
        val accountsDbAdapter = this.accountsDbAdapter

        val account = Account("Basic Account")
        val transaction = Transaction("One transaction")
        transaction.addSplit(Split(createZeroInstance("EUR"), account))
        account.addTransaction(transaction)

        accountsDbAdapter.addRecord(account)

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path!!)
        assertThat(file).exists()
        assertThat(file.name).endsWith(".qif")
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
        val accountsDbAdapter = this.accountsDbAdapter

        val account = Account("Basic Account", Commodity.getInstance("EUR"))
        val transaction = Transaction("One transaction")
        transaction.addSplit(Split(createZeroInstance("EUR"), account))
        account.addTransaction(transaction)
        accountsDbAdapter.addRecord(account)

        val foreignAccount = Account("US Konto", Commodity.getInstance("USD"))
        val multiCurr = Transaction("multi-currency")
        val split1 = Split(Money("12", "USD"), Money("15", "EUR"), foreignAccount)
        val split2 = split1.createPair(account)
        multiCurr.addSplit(split1)
        multiCurr.addSplit(split2)
        foreignAccount.addTransaction(multiCurr)

        accountsDbAdapter.addRecord(foreignAccount)

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path!!)
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
        val expectedNumber = "n123"
        val expectedAccountName = "Basic Account"
        val expectedTime = Calendar.getInstance().apply {
            set(2025, Calendar.SEPTEMBER, 12)
        }.timeInMillis

        val accountsDbAdapter = this.accountsDbAdapter

        val account = Account(expectedAccountName)
        val transaction = Transaction("One transaction")
        transaction.addSplit(Split(Money(-123.45, "EUR"), account))
        transaction.description = expectedDescription
        transaction.notes = expectedMemo
        transaction.number = expectedNumber
        transaction.time = expectedTime
        account.addTransaction(transaction)

        accountsDbAdapter.addRecord(account)

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path!!)
        assertThat(file).exists()
        assertThat(file.name).endsWith(".qif")
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
        assertThat(lines[5]).isEqualTo(QifHelper.DATE_PREFIX + "2025/9/12")
        assertThat(lines[6]).isEqualTo(QifHelper.CATEGORY_PREFIX + "[" + expectedAccountName + "]")
        assertThat(lines[7]).isEqualTo(QifHelper.PAYEE_PREFIX + expectedDescription)
        assertThat(lines[8]).isEqualTo(QifHelper.NUMBER_PREFIX + expectedNumber)
        assertThat(lines[9]).isEqualTo(QifHelper.MEMO_PREFIX + expectedMemo)
        assertThat(lines[10]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[Imbalance-USD]")
        assertThat(lines[11]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "-123.45")
        assertThat(lines[12]).isEqualTo(QifHelper.TOTAL_AMOUNT_PREFIX + "-123.45")
        assertThat(lines[13]).isEqualTo(QifHelper.ENTRY_TERMINATOR)
        file.delete()
    }

    /**
     * Tests exporting a simple transaction with default splits.
     */
    @Test
    fun simpleTransactionExport() {
        val bookUID = importGnuCashXml("simpleTransactionImport.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()
        markForExport()

        assertThat(transactionsDbAdapter.recordsCount).isOne()

        val transaction = transactionsDbAdapter.getRecord("b33c8a6160494417558fd143731fc26a")
        assertThat(transaction.splits).hasSize(2)

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path!!)
        assertThat(file).exists()
        assertThat(file.name).endsWith(".qif")
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
        markForExport()

        assertThat(transactionsDbAdapter.recordsCount).isOne()

        val transaction = transactionsDbAdapter.getRecord("042ff745a80e94e6237fb0549f6d32ae")

        // Ensure it's the correct one
        assertThat(transaction.description).isEqualTo("Tandoori Mahal")

        // Check splits
        assertThat(transaction.splits).hasSize(3)

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path!!)
        assertThat(file).exists()
        assertThat(file.name).endsWith(".qif")
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
        assertThat(lines[9]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[Assets:Cash in Wallet]")
        assertThat(lines[10]).isEqualTo(QifHelper.SPLIT_MEMO_PREFIX + "tip")
        assertThat(lines[11]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "5.00")
        assertThat(lines[12]).isEqualTo(QifHelper.SPLIT_CATEGORY_PREFIX + "[Assets:Bank]")
        assertThat(lines[13]).isEqualTo(QifHelper.SPLIT_AMOUNT_PREFIX + "45.00")
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
        val expectedTime = Calendar.getInstance().apply {
            set(2025, Calendar.JULY, 29)
        }.timeInMillis

        val accountsDbAdapter = this.accountsDbAdapter
        val account1 = Account(expectedAccountName1, Commodity.EUR)
        account1.accountType = AccountType.EXPENSE
        account1.setUID("account-001")
        accountsDbAdapter.addRecord(account1)
        val account2 = Account(expectedAccountName2, Commodity.EUR)
        account2.accountType = AccountType.CASH
        account2.setUID("account-002")
        accountsDbAdapter.addRecord(account2)

        val transactionsDbAdapter = this.transactionsDbAdapter
        val transaction = Transaction("One transaction")
        val split1 = Split(Money(-123.45, Commodity.EUR), account1)
        val split2 = split1.createPair(account2)
        split2.accountUID = account2.uid
        transaction.addSplit(split1)
        transaction.addSplit(split2)
        transaction.description = expectedDescription
        transaction.notes = expectedMemo
        transaction.time = expectedTime
        transactionsDbAdapter.addRecord(transaction)

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exporter = QifExporter(context, exportParameters, bookUID)
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = File(exportedFile!!.path!!)
        assertThat(file).exists()
        assertThat(file.name).endsWith(".qif")
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
        assertThat(lines[5]).isEqualTo(QifHelper.DATE_PREFIX + "2025/7/29")
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
    private fun readFileContent(input: InputStream): String {
        val reader = BufferedReader(InputStreamReader(input))
        val fileContentsBuilder = StringBuilder()
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            fileContentsBuilder.append(line).append('\n')
        }
        return fileContentsBuilder.toString()
    }

    @Throws(IOException::class)
    fun readFileContent(file: File): String {
        val fileInput = FileInputStream(file)
        return readFileContent(fileInput)
    }

    @Test
    fun `export common_1`() {
        val bookUID = importGnuCashXml("common_1.gnucash")
        assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")
        markForExport()

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exportedFile = QifExporter(context, exportParameters, bookUID).export()

        assertThat(exportedFile).isNotNull()
        val zipped = exportedFile!!.toFile()
        assertThat(zipped).isNotEmpty
        val input = ZipInputStream(zipped.inputStream())

        val entry1 = input.nextEntry
        assertThat(entry1.name).endsWith("EUR.qif")
        val textActual1 = input.readAllBytes().toString(StandardCharsets.UTF_8)
        assertThat(textActual1).isNotEmpty
        val textExpected1 = openResourceStream("expected.common_1_EUR.qif")
            .bufferedReader().readText()
        assertThat(textActual1).isEqualTo(textExpected1)

        val entry2 = input.nextEntry
        assertThat(entry2.name).endsWith("USD.qif")
        val textActual2 = input.readAllBytes().toString(StandardCharsets.UTF_8)
        assertThat(textActual2).isNotEmpty
        val textExpected2 = openResourceStream("expected.common_1_USD.qif")
            .bufferedReader().readText()
        assertThat(textActual2).isEqualTo(textExpected2)
    }

    @Test
    fun `export since 2025-03-28`() {
        val bookUID = importGnuCashXml("common_1.gnucash")
        assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")

        // Mark the transactions posted after 2025-03-30.
        val t1 = transactionsDbAdapter.getRecord("46b64b7d40194fbb8270892c171d7c93")
        assertThat(t1.isExported).isTrue
        val where = TransactionEntry.COLUMN_TIMESTAMP + " >= " +
                TimestampHelper.getTimestampFromUtcString("2025-03-30 00:00:00").time
        markForExport(where)
        val t2 = transactionsDbAdapter.getRecord("46b64b7d40194fbb8270892c171d7c93")
        assertThat(t2.isExported).isFalse

        val exportParameters = ExportParams(ExportFormat.QIF)

        val exportedFile = QifExporter(context, exportParameters, bookUID).export()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        assertThat(file.name).endsWith("USD.qif")
        assertThat(file).isNotEmpty
        val input = file.inputStream()

        val textActual1 = input.readAllBytes().toString(StandardCharsets.UTF_8)
        assertThat(textActual1).isNotEmpty
        val textExpected1 = openResourceStream("expected.common_1_USD_20250330.qif")
            .bufferedReader().readText()
        assertThat(textActual1).isEqualTo(textExpected1)
    }
}