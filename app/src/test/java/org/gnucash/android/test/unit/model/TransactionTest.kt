package org.gnucash.android.test.unit.model

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test

class TransactionTest : GnuCashTest() {
    @Test
    fun testCloningTransaction() {
        val transaction = Transaction("Bobba Fett")
        assertThat(transaction.uid).isNotNull()
        assertThat(transaction.currencyCode).isEqualTo(Commodity.DEFAULT_COMMODITY.currencyCode)

        val clone1 = Transaction(transaction, false)
        assertThat(transaction.uid).isEqualTo(clone1.uid)
        assertThat(transaction).isEqualTo(clone1)

        val clone2 = Transaction(transaction, true)
        assertThat(transaction.uid).isNotEqualTo(clone2.uid)
        assertThat(transaction.currencyCode).isEqualTo(clone2.currencyCode)
        assertThat(transaction.description).isEqualTo(clone2.description)
        assertThat(transaction.note).isEqualTo(clone2.note)
        assertThat(transaction.time).isEqualTo(clone2.time)
        //TODO: Clone the created_at and modified_at times?
    }

    /**
     * Adding a split to a transaction should set the transaction UID of the split to the GUID of the transaction
     */
    @Test
    fun addingSplitsShouldSetTransactionUID() {
        val transaction = Transaction("")
        assertThat(transaction.currencyCode).isEqualTo(Commodity.DEFAULT_COMMODITY.currencyCode)

        val split = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), "test-account")
        assertThat(split.transactionUID).isNullOrEmpty()

        transaction.addSplit(split)
        assertThat(split.transactionUID).isEqualTo(transaction.uid)
    }

    @Test
    fun settingUID_shouldSetTransactionUidOfSplits() {
        val t1 = Transaction("Test")
        val split1 = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), "random")
        split1.transactionUID = "non-existent"

        val split2 = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), "account-something")
        split2.transactionUID = "pre-existent"

        val splits = listOf(split1, split2)

        t1.splits = splits

        val transactionUID = assertThat(t1.splits).extracting("transactionUID", String::class.java)
        transactionUID.contains(t1.uid)
        transactionUID.doesNotContain("non-existent")
        transactionUID.doesNotContain("pre-existent")
    }

    @Test
    fun testCreateAutoBalanceSplit() {
        val transactionCredit = Transaction("Transaction with more credit")
        transactionCredit.commodity = Commodity.getInstance("EUR")
        val creditSplit = Split(Money("1", "EUR"), "test-account")
        creditSplit.type = TransactionType.CREDIT
        transactionCredit.addSplit(creditSplit)
        val debitBalanceSplit = transactionCredit.createAutoBalanceSplit()

        assertThat(creditSplit.value.isNegative).isFalse()
        assertThat(debitBalanceSplit!!.value).isEqualTo(creditSplit.value)

        assertThat(creditSplit.quantity.isNegative).isFalse()
        assertThat(debitBalanceSplit.quantity).isEqualTo(creditSplit.quantity)

        val transactionDebit = Transaction("Transaction with more debit")
        transactionDebit.commodity = Commodity.getInstance("EUR")
        val debitSplit = Split(Money("1", "EUR"), "test-account")
        debitSplit.type = TransactionType.DEBIT
        transactionDebit.addSplit(debitSplit)
        val creditBalanceSplit = transactionDebit.createAutoBalanceSplit()

        assertThat(debitSplit.value.isNegative).isFalse()
        assertThat(creditBalanceSplit!!.value).isEqualTo(debitSplit.value)

        assertThat(debitSplit.quantity.isNegative).isFalse()
        assertThat(creditBalanceSplit.quantity).isEqualTo(debitSplit.quantity)
    }
}
