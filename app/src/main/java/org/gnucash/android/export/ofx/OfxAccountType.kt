package org.gnucash.android.export.ofx

import org.gnucash.android.model.AccountType

/**
 * Accounts types which are used by the OFX standard
 */
enum class OfxAccountType {
    CHECKING,
    SAVINGS,
    MONEYMRKT,
    CREDITLINE;

    companion object {
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
        fun of(accountType: AccountType?): OfxAccountType {
            return when (accountType) {
                AccountType.CREDIT,
                AccountType.LIABILITY -> CREDITLINE

                AccountType.CASH,
                AccountType.INCOME,
                AccountType.EXPENSE,
                AccountType.PAYABLE,
                AccountType.RECEIVABLE -> CHECKING

                AccountType.BANK,
                AccountType.ASSET -> SAVINGS

                AccountType.MUTUAL,
                AccountType.STOCK,
                AccountType.EQUITY,
                AccountType.CURRENCY -> MONEYMRKT

                else -> CHECKING
            }
        }
    }
}