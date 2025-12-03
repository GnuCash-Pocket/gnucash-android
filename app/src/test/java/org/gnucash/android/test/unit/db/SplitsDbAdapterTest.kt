/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.database.sqlite.SQLiteException
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Some tests for the splits database adapter
 */
class SplitsDbAdapterTest : GnuCashTest() {
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter
    private lateinit var splitsDbAdapter: SplitsDbAdapter

    private lateinit var account: Account

    @Before
    fun setUp() {
        splitsDbAdapter = SplitsDbAdapter.instance
        transactionsDbAdapter = TransactionsDbAdapter.instance
        accountsDbAdapter = AccountsDbAdapter.instance
        account = Account("Test account")
        accountsDbAdapter.addRecord(account)
    }

    @After
    fun tearDown() {
        accountsDbAdapter.deleteAllRecords()
    }

    /**
     * Adding a split where the account does not exist in the database should generate an exception
     */
    @Test(expected = SQLiteException::class)
    fun shouldHaveAccountInDatabase() {
        val transaction = Transaction("")
        transactionsDbAdapter.addRecord(transaction)

        val split = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), "non-existent")
        split.transactionUID = transaction.uid
        splitsDbAdapter.addRecord(split)
    }

    /**
     * Adding a split where the account does not exist in the database should generate an exception
     */
    @Test(expected = SQLiteException::class)
    fun shouldHaveTransactionInDatabase() {
        val transaction = Transaction("") //not added to the db

        val split = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), account)
        split.transactionUID = transaction.uid
        splitsDbAdapter.addRecord(split)
    }

    @Test
    fun testAddSplit() {
        val transaction = Transaction("")
        transactionsDbAdapter.addRecord(transaction)

        val split = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), account)
        split.transactionUID = transaction.uid
        splitsDbAdapter.addRecord(split)

        val splits = splitsDbAdapter.getSplitsForTransaction(transaction.uid)
        assertThat(splits).isNotEmpty()
        assertThat(splits[0].uid).isEqualTo(split.uid)
    }

    /**
     * When a split is added or modified to a transaction, we should set the
     */
    @Test
    fun addingSplitShouldUnsetExportedFlagOfTransaction() {
        val transaction = Transaction("")
        transaction.isExported = true
        transactionsDbAdapter.addRecord(transaction)

        assertThat(transaction.isExported).isTrue()

        val split = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), account)
        split.transactionUID = transaction.uid
        splitsDbAdapter.addRecord(split)

        val isExported = transactionsDbAdapter.getAttribute(
            transaction.uid,
            TransactionEntry.COLUMN_EXPORTED
        ).toBoolean()
        assertThat(isExported).isFalse()
    }
}
