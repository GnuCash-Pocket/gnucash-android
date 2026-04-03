package org.gnucash.android.test.unit.export

import android.content.SharedPreferences
import androidx.core.content.edit
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.getBookPreferences
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.ui.settings.dialog.DeleteAllTransactionsConfirmationDialog.Companion.deleteAllTransactions
import org.junit.Test

class TransactionsTest : BookHelperTest() {

    @Test
    fun `opening balances after delete all`() {
        val bookUID = importGnuCashXml("common_1.gnucash")
        assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")
        val transactionsDbAdapter = accountsDbAdapter.transactionsDbAdapter

        // enable opening balances.
        val preferences: SharedPreferences = getBookPreferences(context)
        preferences.edit {
            putBoolean(context.getString(R.string.key_save_opening_balances), true)
        }
        val description = context.getString(R.string.account_name_opening_balances)
        val balanceAccountUID = accountsDbAdapter.getOrCreateOpeningBalanceAccountUID
        assertThat(balanceAccountUID).isEqualTo("9947bc317c6349aa806348b4710084ea")

        val txCount = transactionsDbAdapter.recordsCount
        assertThat(txCount).isEqualTo(3)

        val tx0 = transactionsDbAdapter.getRecord("ca215bd292094eae8267d4cba5e6a0f1")
        assertThat(tx0.isTemplate).isFalse()
        assertThat(tx0.description).isEqualTo("Amazon")
        assertThat(tx0.splits[0].value).isEqualTo(Money(12345, 100, Commodity.USD))
        assertThat(tx0.splits[0].accountUID).isEqualTo("0c1ed5137c5d43c0a2d5668c7ae89d72")
        assertThat(tx0.splits[1].accountUID).isEqualTo("377cc9fff6ad44daa3873e070afaf2e1")

        val tx1 = transactionsDbAdapter.getRecord("8cd6e083bef74250b0aa3049f7e81084")
        assertThat(tx1.isTemplate).isFalse()
        assertThat(tx1.description).isEqualTo("Verizon")
        assertThat(tx1.splits[0].value).isEqualTo(Money(100000, 100, Commodity.USD))
        assertThat(tx1.splits[0].accountUID).isEqualTo("84e8882f38514ee8b9c7ad6ede968a8b")
        assertThat(tx1.splits[1].accountUID).isEqualTo("d6572e1cc36c4fa0996598fbece22fe7")

        val tx2 = transactionsDbAdapter.getRecord("46b64b7d40194fbb8270892c171d7c93")
        assertThat(tx2.isTemplate).isFalse()
        assertThat(tx2.description).isEqualTo("mom")
        assertThat(tx2.splits[0].value).isEqualTo(Money(88800, 100, Commodity.USD))
        assertThat(tx2.splits[0].accountUID).isEqualTo("f973a7c67330448bb6dda4ed40b45d81")
        assertThat(tx2.splits[1].accountUID).isEqualTo("5cd48b4cbd9b492db920690207d5d89b")

        val tx3 = transactionsDbAdapter.getRecord("9b42fbb885db4918819a05fc42dd63e0")
        assertThat(tx3.isTemplate).isTrue()
        assertThat(tx3.description).isEqualTo("AT&T")
        assertThat(tx3.splits[0].value).isEqualTo(Money(999, 10, Commodity.getInstance("ILS")))

        deleteAllTransactions(accountsDbAdapter)

        val txCountAfterDelete = transactionsDbAdapter.recordsCount
        assertThat(txCountAfterDelete).isEqualTo(6) // each account has its own opening balance now.

        val txsAfter = transactionsDbAdapter.allRecords
        assertThat(txsAfter.size).isEqualTo(7)

        val tx0After = txsAfter[0]
        assertThat(tx0After.uid).isEqualTo("9b42fbb885db4918819a05fc42dd63e0")
        assertThat(tx0After.isTemplate).isTrue()
        assertThat(tx0After.description).isEqualTo("AT&T")

        val tx1After = txsAfter[1]
        assertThat(tx1After.isTemplate).isFalse()
        assertThat(tx1After.description).isEqualTo(description)
        assertThat(tx1After.splits[0].value).isEqualTo(Money(12345, 100, Commodity.USD))
        assertThat(tx1After.splits[0].accountUID).isEqualTo("377cc9fff6ad44daa3873e070afaf2e1")
        assertThat(tx1After.splits[1].accountUID).isEqualTo(balanceAccountUID)

        val tx2After = txsAfter[2]
        assertThat(tx2After.isTemplate).isFalse()
        assertThat(tx2After.description).isEqualTo(description)
        assertThat(tx2After.splits[0].value).isEqualTo(Money(93000, 100, Commodity.EUR))
        assertThat(tx2After.splits[0].accountUID).isEqualTo("d6572e1cc36c4fa0996598fbece22fe7")
        assertThat(tx2After.splits[1].accountUID).isEqualTo(balanceAccountUID)

        val tx3After = txsAfter[3]
        assertThat(tx3After.isTemplate).isFalse()
        assertThat(tx3After.description).isEqualTo(description)
        assertThat(tx3After.splits[0].value).isEqualTo(Money(133253, 1, Commodity.JPY))
        assertThat(tx3After.splits[0].accountUID).isEqualTo("f973a7c67330448bb6dda4ed40b45d81")
        assertThat(tx3After.splits[1].accountUID).isEqualTo(balanceAccountUID)

        val tx4After = txsAfter[4]
        assertThat(tx4After.isTemplate).isFalse()
        assertThat(tx4After.description).isEqualTo(description)
        assertThat(tx4After.splits[0].value).isEqualTo(Money(12345, 100, Commodity.USD))
        assertThat(tx4After.splits[0].accountUID).isEqualTo("0c1ed5137c5d43c0a2d5668c7ae89d72")
        assertThat(tx4After.splits[1].accountUID).isEqualTo(balanceAccountUID)

        val tx5After = txsAfter[5]
        assertThat(tx5After.isTemplate).isFalse()
        assertThat(tx5After.description).isEqualTo(description)
        assertThat(tx5After.splits[0].value).isEqualTo(Money(100000, 100, Commodity.USD))
        assertThat(tx5After.splits[0].accountUID).isEqualTo("84e8882f38514ee8b9c7ad6ede968a8b")
        assertThat(tx5After.splits[1].accountUID).isEqualTo(balanceAccountUID)

        val tx6After = txsAfter[6]
        assertThat(tx6After.isTemplate).isFalse()
        assertThat(tx6After.description).isEqualTo(description)
        assertThat(tx6After.splits[0].value).isEqualTo(Money(88800, 100, Commodity.USD))
        assertThat(tx6After.splits[0].accountUID).isEqualTo("5cd48b4cbd9b492db920690207d5d89b")
        assertThat(tx6After.splits[1].accountUID).isEqualTo(balanceAccountUID)
    }
}