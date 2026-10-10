package org.gnucash.android.test.unit.model

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.export.csv.CsvTransactionsExporter.Companion.parseSplit
import org.gnucash.android.export.csv.CsvTransactionsExporter.Companion.toCsv
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
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

        val clone1 = split.copy(false)
        assertThat(clone1).isEqualTo(split)

        val clone2 = split.copy(true)
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

    @Test
    fun `plusAssign - credit and credit`() {
        val split1 = Split(Money("100", "USD"), "imbalance")
        split1.type = TransactionType.CREDIT
        val split2 = Split(Money("50", "USD"), "imbalance")
        split2.type = TransactionType.CREDIT

        split1 += split2

        assertThat(split1.type).isEqualTo(TransactionType.CREDIT)
        assertThat(split1.value).isEqualTo(Money("150", "USD"))
        assertThat(split1.quantity).isEqualTo(Money("150", "USD"))
    }

    @Test
    fun `plusAssign - debit and debit`() {
        val split1 = Split(Money("100", "USD"), "imbalance")
        split1.type = TransactionType.DEBIT
        val split2 = Split(Money("50", "USD"), "imbalance")
        split2.type = TransactionType.DEBIT

        split1 += split2

        assertThat(split1.type).isEqualTo(TransactionType.DEBIT)
        assertThat(split1.value).isEqualTo(Money("150", "USD"))
        assertThat(split1.quantity).isEqualTo(Money("150", "USD"))
    }

    @Test
    fun `plusAssign - credit and debit`() {
        val split1 = Split(Money("499", "USD"), "imbalance")
        split1.type = TransactionType.CREDIT
        val split2 = Split(Money("99", "USD"), "imbalance")
        split2.type = TransactionType.DEBIT

        split1 += split2

        assertThat(split1.type).isEqualTo(TransactionType.CREDIT)
        assertThat(split1.value).isEqualTo(Money("400", "USD"))
        assertThat(split1.quantity).isEqualTo(Money("400", "USD"))
    }

    @Test
    fun `plusAssign - credit and big debit`() {
        val split1 = Split(Money("499", "USD"), "imbalance")
        split1.type = TransactionType.CREDIT
        val split2 = Split(Money("599", "USD"), "imbalance")
        split2.type = TransactionType.DEBIT

        split1 += split2

        assertThat(split1.type).isEqualTo(TransactionType.DEBIT)
        assertThat(split1.value).isEqualTo(Money("100", "USD"))
        assertThat(split1.quantity).isEqualTo(Money("100", "USD"))
    }

    @Test
    fun `plusAssign - debit and credit`() {
        val split1 = Split(Money("499", "USD"), "imbalance")
        split1.type = TransactionType.DEBIT
        val split2 = Split(Money("99", "USD"), "imbalance")
        split2.type = TransactionType.CREDIT

        split1 += split2

        assertThat(split1.type).isEqualTo(TransactionType.DEBIT)
        assertThat(split1.value).isEqualTo(Money("400", "USD"))
        assertThat(split1.quantity).isEqualTo(Money("400", "USD"))
    }

    @Test
    fun `plusAssign - debit and big credit`() {
        val split1 = Split(Money("499", "USD"), "imbalance")
        split1.type = TransactionType.DEBIT
        val split2 = Split(Money("599", "USD"), "imbalance")
        split2.type = TransactionType.CREDIT

        split1 += split2

        assertThat(split1.type).isEqualTo(TransactionType.CREDIT)
        assertThat(split1.value).isEqualTo(Money("100", "USD"))
        assertThat(split1.quantity).isEqualTo(Money("100", "USD"))
    }
}
