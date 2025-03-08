package org.gnucash.android.test.unit.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.Test;

import java.math.BigDecimal;

/**
 * Test cases for Splits
 *
 * @author Ngewi
 */
public class SplitTest extends GnuCashTest {
    @Test
    public void amounts_shouldBeStoredUnsigned() {
        Split split = new Split(new Money("-1", "USD"), new Money("-2", "EUR"), "account-UID");
        assertThat(split.getValue().isNegative()).isFalse();
        assertThat(split.getQuantity().isNegative()).isFalse();

        split.setValue(new Money("-3", "USD"));
        split.setQuantity(new Money("-4", "EUR"));
        assertThat(split.getValue().isNegative()).isFalse();
        assertThat(split.getQuantity().isNegative()).isFalse();
    }

    @Test
    public void testAddingSplitToTransaction() {
        Split split = new Split(Money.createZeroInstance(Commodity.DEFAULT_COMMODITY), "Test");
        assertThat(split.getTransactionUID()).isEmpty();

        Transaction transaction = new Transaction("Random");
        transaction.addSplit(split);

        assertThat(transaction.getUID()).isEqualTo(split.getTransactionUID());

    }

    @Test
    public void testCloning() {
        Split split = new Split(new Money(BigDecimal.TEN, Commodity.getInstance("EUR")), "random-account");
        split.setTransactionUID("terminator-trx");
        split.setType(TransactionType.CREDIT);

        Split clone1 = new Split(split, false);
        assertThat(clone1).isEqualTo(split);

        Split clone2 = new Split(split, true);
        assertThat(clone2.getUID()).isNotEqualTo(split.getUID());
        assertThat(split.isEquivalentTo(clone2)).isTrue();
    }

    /**
     * Tests that a split pair has the inverse transaction type as the origin split.
     * Everything else should be the same
     */
    @Test
    public void shouldCreateInversePair() {
        Split split = new Split(new Money("2", "USD"), "dummy");
        split.setType(TransactionType.CREDIT);
        split.setTransactionUID("random-trx");
        Split pair = split.createPair("test");

        assertThat(pair.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(pair.getValue()).isEqualTo(split.getValue());
        assertThat(pair.getMemo()).isEqualTo(split.getMemo());
        assertThat(pair.getTransactionUID()).isEqualTo(split.getTransactionUID());
    }

    @Test
    public void shouldGenerateValidCsv() {
        Split split = new Split(new Money(BigDecimal.TEN, Commodity.getInstance("EUR")), "random-account");
        split.setTransactionUID("terminator-trx");
        split.setType(TransactionType.CREDIT);

        assertThat(split.toCsv()).isEqualTo(split.getUID() + ";1000;100;EUR;1000;100;EUR;terminator-trx;random-account;CREDIT");
    }

    @Test
    public void shouldParseCsv() {
        String csv = "test-split-uid;490;100;USD;490;100;USD;trx-action;test-account;DEBIT;Didn't you get the memo?";
        Split split = Split.parseSplit(csv);
        assertThat(split.getValue().getNumerator()).isEqualTo(new Money("4.90", "USD").getNumerator());
        assertThat(split.getTransactionUID()).isEqualTo("trx-action");
        assertThat(split.getAccountUID()).isEqualTo("test-account");
        assertThat(split.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split.getMemo()).isEqualTo("Didn't you get the memo?");
    }
}
