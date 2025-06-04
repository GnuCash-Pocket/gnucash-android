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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Index
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class TransactionsDbAdapterTest : GnuCashTest() {
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter
    private lateinit var splitsDbAdapter: SplitsDbAdapter
    private lateinit var alphaAccount: Account
    private lateinit var bravoAccount: Account
    private lateinit var testSplit: Split

    @Before
    fun setUp() {
        splitsDbAdapter = SplitsDbAdapter.getInstance()
        transactionsDbAdapter = TransactionsDbAdapter.getInstance()
        accountsDbAdapter = AccountsDbAdapter.getInstance()

        alphaAccount = Account(ALPHA_ACCOUNT_NAME)
        bravoAccount = Account(BRAVO_ACCOUNT_NAME)

        accountsDbAdapter.addRecord(bravoAccount)
        accountsDbAdapter.addRecord(alphaAccount)

        testSplit = Split(Money(BigDecimal.TEN, alphaAccount.commodity), alphaAccount.uid)
    }

    @After
    fun tearDown() {
        accountsDbAdapter.deleteAllRecords()
    }

    @Test
    fun testTransactionsAreTimeSorted() {
        val t1 = Transaction("T800")
        t1.setTime(System.currentTimeMillis() - 10000)
        val split = Split(createZeroInstance(alphaAccount.commodity), alphaAccount.uid)
        t1.addSplit(split)
        t1.addSplit(split.createPair(bravoAccount.uid))

        val t2 = Transaction("T1000")
        t2.setTime(System.currentTimeMillis())
        val split2 = Split(Money("23.50", bravoAccount.commodity), bravoAccount.uid)
        t2.addSplit(split2)
        t2.addSplit(split2.createPair(alphaAccount.uid))

        transactionsDbAdapter.addRecord(t1)
        transactionsDbAdapter.addRecord(t2)

        val transactionsList = transactionsDbAdapter.getAllTransactionsForAccount(
            alphaAccount.uid
        )
        assertThat(transactionsList).contains(t2, Index.atIndex(0))
        assertThat(transactionsList).contains(t1, Index.atIndex(1))
    }

    @Test
    fun deletingTransactionsShouldDeleteSplits() {
        val transaction = Transaction("")
        val split = Split(createZeroInstance(alphaAccount.commodity), alphaAccount.uid)
        transaction.addSplit(split)
        transactionsDbAdapter.addRecord(transaction)

        assertThat(splitsDbAdapter.getSplitsForTransaction(transaction.uid)).hasSize(1)

        transactionsDbAdapter.deleteRecord(transaction.uid)
        assertThat(splitsDbAdapter.getSplitsForTransaction(transaction.uid)).isEmpty()
    }

    @Test
    fun shouldBalanceTransactionsOnSave() {
        val transaction = Transaction("Auto balance")
        val split = Split(Money(BigDecimal.TEN, alphaAccount.commodity), alphaAccount.uid)

        transaction.addSplit(split)

        transactionsDbAdapter.addRecord(transaction)

        val trn = transactionsDbAdapter.getRecord(transaction.uid)
        assertThat(trn.splits).hasSize(2)

        val imbalanceAccountUID =
            accountsDbAdapter.getImbalanceAccountUID(context, Commodity.DEFAULT_COMMODITY)
        assertThat(trn.splits).extracting("accountUID").contains(imbalanceAccountUID)
    }

    @Test
    fun testComputeBalance() {
        var transaction = Transaction("Compute")
        val firstSplitAmount = Money("4.99", alphaAccount.commodity)
        var split = Split(firstSplitAmount, alphaAccount.uid)
        transaction.addSplit(split)
        val secondSplitAmount = Money("3.50", bravoAccount.commodity)
        split = Split(secondSplitAmount, bravoAccount.uid)
        transaction.addSplit(split)

        transactionsDbAdapter.addRecord(transaction)

        //balance is negated because the CASH account has inverse normal balance
        transaction = transactionsDbAdapter.getRecord(transaction.uid)
        var savedBalance = transaction.getBalance(alphaAccount)
        assertThat(savedBalance).isEqualTo(firstSplitAmount.unaryMinus())

        savedBalance = transaction.getBalance(bravoAccount)
        assertThat(savedBalance).isEqualTo(secondSplitAmount.unaryMinus())
        assertThat(savedBalance.commodity).isEqualTo(secondSplitAmount.commodity)
    }

    companion object {
        private const val ALPHA_ACCOUNT_NAME = "Alpha"
        private const val BRAVO_ACCOUNT_NAME = "Bravo"
    }
}
