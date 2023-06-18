package org.gnucash.android.model

/**
 * The type of account
 * This are the different types specified by the OFX format and
 * they are currently not used except for exporting
 */
enum class AccountType {
    CASH(TransactionType.DEBIT),
    BANK(TransactionType.DEBIT),
    CREDIT,
    ASSET(TransactionType.DEBIT),
    LIABILITY,
    INCOME,
    EXPENSE(TransactionType.DEBIT),
    PAYABLE,
    RECEIVABLE(TransactionType.DEBIT),
    EQUITY,
    CURRENCY,
    STOCK(TransactionType.DEBIT),
    MUTUAL(TransactionType.DEBIT),
    TRADING,
    ROOT;
    /**
     * Indicates that this type of normal balance the account type has
     *
     * To increase the value of an account with normal balance of credit, one would credit the account.
     * To increase the value of an account with normal balance of debit, one would likewise debit the account.
     */
    var normalBalanceType = TransactionType.CREDIT
        private set

    constructor(normalBalance: TransactionType) {
        normalBalanceType = normalBalance
    }

    constructor() {
        //nothing to see here, move along
    }

    fun hasDebitNormalBalance(): Boolean {
        return normalBalanceType === TransactionType.DEBIT
    }
}
