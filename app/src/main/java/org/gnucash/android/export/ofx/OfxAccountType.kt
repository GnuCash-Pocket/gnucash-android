package org.gnucash.android.export.ofx

import org.gnucash.android.model.AccountType

/**
 * Accounts types which are used by the OFX standard
 */
enum class OfxAccountType(val value: String) {
    // Checking
    CHECKING("CHECKING"),

    // Savings
    SAVINGS("SAVINGS"),

    // Money Market
    MONEY_MARKET("MONEYMRKT"),

    // Line of credit
    CREDIT_LINE("CREDITLINE");

    override fun toString(): String {
        return value
    }

    companion object {
        private val accountTypes = mapOf<AccountType, OfxAccountType>(
            AccountType.CASH to CHECKING,
            AccountType.INCOME to CHECKING,
            AccountType.EXPENSE to CHECKING,
            AccountType.PAYABLE to CHECKING,
            AccountType.RECEIVABLE to CHECKING,

            AccountType.CREDIT to CREDIT_LINE,
            AccountType.LIABILITY to CREDIT_LINE,

            AccountType.MUTUAL to MONEY_MARKET,
            AccountType.STOCK to MONEY_MARKET,
            AccountType.EQUITY to MONEY_MARKET,
            AccountType.CURRENCY to MONEY_MARKET,

            AccountType.BANK to SAVINGS,
            AccountType.ASSET to SAVINGS,
        )

        /**
         * Maps the `accountType` to the corresponding account type.
         * `accountType` have corresponding values to GnuCash desktop
         *
         * @param accountType [AccountType] of an account
         * @return Corresponding [OfxAccountType] for the `accountType`
         * @see AccountType
         *
         * @see OfxAccountType
         */
        fun of(accountType: AccountType): OfxAccountType {
            return accountTypes[accountType] ?: CHECKING
        }
    }
}