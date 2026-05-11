package org.gnucash.android.test.unit.db

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.BookDbHelper
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.test.unit.BookHelperTest
import org.junit.Test

class BooksTest : BookHelperTest() {
    @Test
    fun `duplicate accounts`() {
        booksDbAdapter.deleteAllRecords()

        val bookUID = importGnuCashXml("common_1.gnucash")
        assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")
        assertThat(booksDbAdapter.recordsCount).isOne
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(69)
        assertThat(transactionsDbAdapter.recordsCount).isEqualTo(3)
        assertThat(scheduledActionDbAdapter.recordsCount).isOne

        val helper = BookDbHelper(context)
        val bookNew = helper.duplicateAccounts(bookUID)
        assertThat(bookNew.uid).isNotEqualTo(bookUID)
        assertThat(booksDbAdapter.recordsCount).isEqualTo(2)

        val databaseHelper = DatabaseHelper(context, bookNew.uid)
        val mainHolder = databaseHelper.holder
        val commoditiesDbAdapter = CommoditiesDbAdapter(mainHolder)
        val transactionsDbAdapter = TransactionsDbAdapter(commoditiesDbAdapter)
        val recurrenceDbAdapter = RecurrenceDbAdapter(mainHolder)
        val accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter)
        val scheduledActionDbAdapter =
            ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter)

        assertThat(accountsDbAdapter.recordsCount).isEqualTo(69)
        assertThat(transactionsDbAdapter.recordsCount).isZero
        assertThat(scheduledActionDbAdapter.recordsCount).isZero

        mainHolder.close()
    }
}