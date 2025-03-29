package org.gnucash.android.model

/**
 * The type of account
 * This are the different types specified by the OFX format and
 * they are currently not used except for exporting
 */
enum class AccountType(
    /**
     * Indicates that this type of normal balance the account type has
     *
     * To increase the value of an account with normal balance of credit, one would credit the account.
     * To increase the value of an account with normal balance of debit, one would likewise debit the account.
     */
    @JvmField
    val normalBalanceType: TransactionType,
    @JvmField
    val displayBalanceType: TransactionType = normalBalanceType,
    // Index of the label in the array of strings `account_type_entry_values`
    @JvmField
    val labelIndex: Int
) {
    /**< The bank account type denotes a savings
     *   or checking account held at a bank.
     *   Often interest bearing. */
    BANK(TransactionType.DEBIT, labelIndex = 1),

    /**< The cash account type is used to denote a
     *   shoe-box or pillowcase stuffed with *
     *   cash. */
    CASH(TransactionType.DEBIT, labelIndex = 0),

    /**< The Credit card account is used to denote
     *   credit (e.g. amex) and debit (e.g. visa,
     *   mastercard) card accounts */
    CREDIT(TransactionType.CREDIT, labelIndex = 2),

    /**< asset (and liability) accounts indicate
     *   generic, generalized accounts that are
     *   none of the above. */
    ASSET(TransactionType.DEBIT, labelIndex = 3),

    /**< liability (and asset) accounts indicate
     *   generic, generalized accounts that are
     *   none of the above. */
    LIABILITY(TransactionType.CREDIT, TransactionType.DEBIT, labelIndex = 4),

    /**< Stock accounts will typically be shown in
     *   registers which show three columns:
     *   price, number of shares, and value. */
    STOCK(TransactionType.DEBIT, labelIndex = 11),

    /**< Mutual Fund accounts will typically be
     *   shown in registers which show three
     *   columns: price, number of shares, and
     *   value. */
    MUTUAL(TransactionType.DEBIT, labelIndex = 12),

    /**< The currency account type indicates that
     *   the account is a currency trading
     *   account.  In many ways, a currency
     *   trading account is like a stock *
     *   trading account. It is shown in the
     *   register with three columns: price,
     *   number of shares, and value. Note:
     *   Since version 1.7.0, this account is *
     *   no longer needed to exchange currencies
     *   between accounts, so this type is
     *   DEPRECATED. */
    @Deprecated("Since GnuCash 1.7.0")
    CURRENCY(TransactionType.CREDIT, labelIndex = 10),

    /**< Income accounts are used to denote
     *   income */
    INCOME(TransactionType.CREDIT, labelIndex = 5),

    /**< Expense accounts are used to denote
     *   expenses. */
    EXPENSE(TransactionType.DEBIT, TransactionType.CREDIT, labelIndex = 6),

    /**< Equity account is used to balance the
     *   balance sheet. */
    EQUITY(TransactionType.CREDIT, labelIndex = 9),

    /**< A/R account type */
    RECEIVABLE(TransactionType.DEBIT, labelIndex = 8),

    /**< A/P account type */
    PAYABLE(TransactionType.CREDIT, labelIndex = 7),

    /**< The hidden root account of an account tree. */
    ROOT(TransactionType.DEBIT, labelIndex = -1),

    /**< Account used to record multiple commodity transactions.
     *   This is not the same as ACCT_TYPE_CURRENCY above.
     *   Multiple commodity transactions have splits in these
     *   accounts to make the transaction balance in each
     *   commodity as well as in total value.  */
    TRADING(TransactionType.CREDIT, labelIndex = 13);

    @JvmField
    val hasDebitNormalBalance: Boolean = normalBalanceType === TransactionType.DEBIT

    @JvmField
    val hasDebitDisplayBalance: Boolean = displayBalanceType === TransactionType.DEBIT

}
