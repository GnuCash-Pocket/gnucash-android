package org.gnucash.android.widget

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch
import org.junit.Test

class TransactionTypeSwitchTest : GnuCashTest() {

    @Test
    fun `credit and debit labels`() {
        val labelBill = context.getString(R.string.label_bill)
        val labelBuy = context.getString(R.string.label_buy)
        val labelCharge = context.getString(R.string.label_charge)
        val labelCredit = context.getString(R.string.label_credit)
        val labelDebit = context.getString(R.string.label_debit)
        val labelDecrease = context.getString(R.string.label_decrease)
        val labelDeposit = context.getString(R.string.label_deposit)
        val labelExpense = context.getString(R.string.label_expense)
        val labelIncome = context.getString(R.string.label_income)
        val labelIncrease = context.getString(R.string.label_increase)
        val labelInvoice = context.getString(R.string.label_invoice)
        val labelPayment = context.getString(R.string.label_payment)
        val labelRebate = context.getString(R.string.label_rebate)
        val labelReceive = context.getString(R.string.label_receive)
        val labelSell = context.getString(R.string.label_sell)
        val labelSpend = context.getString(R.string.label_spend)
        val labelWithdrawal = context.getString(R.string.label_withdrawal)

        val switch = TransactionTypeSwitch(context)

        switch.accountType = AccountType.BANK
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelDeposit)
        assertThat(switch.textCredit).isEqualTo(labelWithdrawal)
        assertThat(switch.textOff).isEqualTo(labelDeposit)
        assertThat(switch.textOn).isEqualTo(labelWithdrawal)

        switch.accountType = AccountType.CASH
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelReceive)
        assertThat(switch.textCredit).isEqualTo(labelSpend)
        assertThat(switch.textOff).isEqualTo(labelReceive)
        assertThat(switch.textOn).isEqualTo(labelSpend)

        switch.accountType = AccountType.CREDIT
        assertThat(switch.accountType.hasDebitNormalBalance).isFalse()
        assertThat(switch.accountType.hasDebitDisplayBalance).isFalse()
        assertThat(switch.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(switch.textDebit).isEqualTo(labelPayment)
        assertThat(switch.textCredit).isEqualTo(labelCharge)
        assertThat(switch.textOff).isEqualTo(labelCharge)
        assertThat(switch.textOn).isEqualTo(labelPayment)

        switch.accountType = AccountType.ASSET
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelIncrease)
        assertThat(switch.textCredit).isEqualTo(labelDecrease)
        assertThat(switch.textOff).isEqualTo(labelIncrease)
        assertThat(switch.textOn).isEqualTo(labelDecrease)

        switch.accountType = AccountType.LIABILITY
        assertThat(switch.accountType.hasDebitNormalBalance).isFalse()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(switch.textDebit).isEqualTo(labelDecrease)
        assertThat(switch.textCredit).isEqualTo(labelIncrease)
        assertThat(switch.textOff).isEqualTo(labelIncrease)
        assertThat(switch.textOn).isEqualTo(labelDecrease)

        switch.accountType = AccountType.STOCK
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelBuy)
        assertThat(switch.textCredit).isEqualTo(labelSell)
        assertThat(switch.textOff).isEqualTo(labelBuy)
        assertThat(switch.textOn).isEqualTo(labelSell)

        switch.accountType = AccountType.MUTUAL
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelBuy)
        assertThat(switch.textCredit).isEqualTo(labelSell)
        assertThat(switch.textOff).isEqualTo(labelBuy)
        assertThat(switch.textOn).isEqualTo(labelSell)

        switch.accountType = AccountType.CURRENCY
        assertThat(switch.accountType.hasDebitNormalBalance).isFalse()
        assertThat(switch.accountType.hasDebitDisplayBalance).isFalse()
        assertThat(switch.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(switch.textDebit).isEqualTo(labelBuy)
        assertThat(switch.textCredit).isEqualTo(labelSell)
        assertThat(switch.textOff).isEqualTo(labelSell)
        assertThat(switch.textOn).isEqualTo(labelBuy)

        switch.accountType = AccountType.INCOME
        assertThat(switch.accountType.hasDebitNormalBalance).isFalse()
        assertThat(switch.accountType.hasDebitDisplayBalance).isFalse()
        assertThat(switch.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(switch.textDebit).isEqualTo(labelCharge)
        assertThat(switch.textCredit).isEqualTo(labelIncome)
        assertThat(switch.textOff).isEqualTo(labelIncome)
        assertThat(switch.textOn).isEqualTo(labelCharge)

        switch.accountType = AccountType.EXPENSE
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isFalse()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelExpense)
        assertThat(switch.textCredit).isEqualTo(labelRebate)
        assertThat(switch.textOff).isEqualTo(labelExpense)
        assertThat(switch.textOn).isEqualTo(labelRebate)

        switch.accountType = AccountType.PAYABLE
        assertThat(switch.accountType.hasDebitNormalBalance).isFalse()
        assertThat(switch.accountType.hasDebitDisplayBalance).isFalse()
        assertThat(switch.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(switch.textDebit).isEqualTo(labelPayment)
        assertThat(switch.textCredit).isEqualTo(labelBill)
        assertThat(switch.textOff).isEqualTo(labelBill)
        assertThat(switch.textOn).isEqualTo(labelPayment)

        switch.accountType = AccountType.RECEIVABLE
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelInvoice)
        assertThat(switch.textCredit).isEqualTo(labelPayment)
        assertThat(switch.textOff).isEqualTo(labelInvoice)
        assertThat(switch.textOn).isEqualTo(labelPayment)

        switch.accountType = AccountType.TRADING
        assertThat(switch.accountType.hasDebitNormalBalance).isFalse()
        assertThat(switch.accountType.hasDebitDisplayBalance).isFalse()
        assertThat(switch.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(switch.textDebit).isEqualTo(labelDecrease)
        assertThat(switch.textCredit).isEqualTo(labelIncrease)
        assertThat(switch.textOff).isEqualTo(labelIncrease)
        assertThat(switch.textOn).isEqualTo(labelDecrease)

        switch.accountType = AccountType.EQUITY
        assertThat(switch.accountType.hasDebitNormalBalance).isFalse()
        assertThat(switch.accountType.hasDebitDisplayBalance).isFalse()
        assertThat(switch.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(switch.textDebit).isEqualTo(labelDecrease)
        assertThat(switch.textCredit).isEqualTo(labelIncrease)
        assertThat(switch.textOff).isEqualTo(labelIncrease)
        assertThat(switch.textOn).isEqualTo(labelDecrease)

        switch.accountType = AccountType.ROOT
        assertThat(switch.accountType.hasDebitNormalBalance).isTrue()
        assertThat(switch.accountType.hasDebitDisplayBalance).isTrue()
        assertThat(switch.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(switch.textDebit).isEqualTo(labelDebit)
        assertThat(switch.textCredit).isEqualTo(labelCredit)
        assertThat(switch.textOff).isEqualTo(labelDebit)
        assertThat(switch.textOn).isEqualTo(labelCredit)
    }
}