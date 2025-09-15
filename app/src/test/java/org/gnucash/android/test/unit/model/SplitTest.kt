package org.gnucash.android.test.unit.model

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Split.Companion.parseSplit
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test
import java.math.BigDecimal

/**
 * Test cases for Splits
 *
 * @author Ngewi
 */
class SplitTest : GnuCashTest() {
    @Test
    fun amounts_shouldBeStoredUnsigned() {
        val split = Split(Money("-1", "USD"), Money("-2", "EUR"), "account-UID")
        assertThat(split.value.isNegative).isFalse()
        assertThat(split.quantity.isNegative).isFalse()

        split.value = Money("-3", "USD")
        split.quantity = Money("-4", "EUR")
        assertThat(split.value.isNegative).isFalse()
        assertThat(split.quantity.isNegative).isFalse()
    }

    @Test
    fun testAddingSplitToTransaction() {
        val split = Split(createZeroInstance(Commodity.DEFAULT_COMMODITY), "Test")
        assertThat(split.transactionUID).isNullOrEmpty()

        val transaction = Transaction("Random")
        transaction.addSplit(split)

        assertThat(transaction.uid).isEqualTo(split.transactionUID)
    }

    @Test
    fun testCloning() {
        val split = Split(Money(BigDecimal.TEN, Commodity.getInstance("EUR")), "random-account")
        split.transactionUID = "terminator-trx"
        split.type = TransactionType.CREDIT

        val clone1 = Split(split, false)
        assertThat(clone1).isEqualTo(split)

        val clone2 = Split(split, true)
        assertThat(clone2.uid).isNotEqualTo(split.uid)
        assertThat(split.isEquivalentTo(clone2)).isTrue()
    }

    /**
     * Tests that a split pair has the inverse transaction type as the origin split.
     * Everything else should be the same
     */
    @Test
    fun shouldCreateInversePair() {
        val split = Split(Money("2", "USD"), "dummy")
        split.type = TransactionType.CREDIT
        split.transactionUID = "random-trx"
        val pair = split.createPair("test")

        assertThat(pair.type).isEqualTo(TransactionType.DEBIT)
        assertThat(pair.value).isEqualTo(split.value)
        assertThat(pair.memo).isEqualTo(split.memo)
        assertThat(pair.transactionUID).isEqualTo(split.transactionUID)
    }

    @Test
    fun shouldGenerateValidCsv() {
        val split = Split(Money(BigDecimal.TEN, Commodity.getInstance("EUR")), "random-account")
        split.transactionUID = "terminator-trx"
        split.type = TransactionType.CREDIT

        assertThat(split.toCsv())
            .isEqualTo(split.uid + ";1000;100;EUR;1000;100;EUR;terminator-trx;random-account;CREDIT")
    }

    @Test
    fun shouldParseCsv() {
        val csv =
            "test-split-uid;490;100;USD;490;100;USD;trx-action;test-account;DEBIT;Didn't you get the memo?"
        val split = parseSplit(csv)
        assertThat(split.value.numerator).isEqualTo(Money("4.90", "USD").numerator)
        assertThat(split.transactionUID).isEqualTo("trx-action")
        assertThat(split.accountUID).isEqualTo("test-account")
        assertThat(split.type).isEqualTo(TransactionType.DEBIT)
        assertThat(split.memo).isEqualTo("Didn't you get the memo?")
    }
}
