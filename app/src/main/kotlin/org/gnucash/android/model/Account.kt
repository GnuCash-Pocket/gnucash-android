/*
* Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.gnucash.android.model

import android.graphics.Color
import org.gnucash.android.BuildConfig
import org.gnucash.android.export.ofx.OfxHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.sql.Timestamp

/**
 * An account represents a transaction account in with [Transaction]s may be recorded
 * Accounts have different types as specified by [AccountType] and also a currency with
 * which transactions may be recorded in the account
 * By default, an account is made an [AccountType.CASH] and the default currency is
 * the currency of the Locale of the device on which the software is running. US Dollars is used
 * if the platform locale cannot be determined.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see AccountType
 */
class Account : BaseModel {
    /**
     * Accounts types which are used by the OFX standard
     */
    enum class OfxAccountType {
        CHECKING, SAVINGS, MONEYMRKT, CREDITLINE
    }

    /**
     * Name of this account
     */
    var name: String? = null
        private set

    /**
     * Fully qualified name of this account including the parent hierarchy.
     * On instantiation of an account, the full name is set to the name by default
     */
    var fullName: String?

    /**
     * Account description
     */
    var description = ""

    /**
     * Commodity used by this account
     */
    private var _commodity: Commodity? = null

    /**
     * Type of account
     * Defaults to [AccountType.CASH]
     */
    var accountType = AccountType.CASH

    /**
     * List of transactions in this account
     */
    private var _transactionsList: MutableList<Transaction> = ArrayList()

    /**
     * Account UID of the parent account. Can be null
     */
    private var _parentAccountUID: String? = null

    /**
     * Save UID of a default account for transfers.
     * All transactions in this account will by default be transfers to the other account
     */
    private var _defaultTransferAccountUID: String? = null

    /**
     * Flag for placeholder accounts.
     * These accounts cannot have transactions
     */
    private var _isPlaceholderAccount = false

    /**
     * Account color field in hex format #rrggbb
     */
    private var _color = DEFAULT_COLOR

    /**
     * `true` if this account is flagged as a favorite account, `false` if not
     */
    var isFavorite = false

    /**
     * Flag which indicates if this account is a hidden account or not
     */
    private var _isHidden = false

    /**
     * Constructor
     * Creates a new account with the default currency and a generated unique ID
     *
     * @param name Name of the account
     */
    constructor(name: String) {
        setName(name)
        fullName = this.name
        commodity = Commodity.DEFAULT_COMMODITY
    }

    /**
     * Overloaded constructor
     *
     * @param name      Name of the account
     * @param commodity [Commodity] to be used by transactions in this account
     */
    constructor(name: String, commodity: Commodity) {
        setName(name)
        fullName = this.name
        this.commodity = commodity
    }

    /**
     * Sets the name of the account
     *
     * @param name String name of the account
     */
    fun setName(name: String) {
        this.name = name.trim { it <= ' ' }
    }

    /**
     * Adds a transaction to this account
     *
     * @param transaction [Transaction] to be added to the account
     */
    fun addTransaction(transaction: Transaction) {
        transaction.commodity = _commodity!!
        _transactionsList.add(transaction)
    }

    /**
     * Sets a list of transactions for this account.
     * Overrides any previous transactions with those in the list.
     * The account UID and currency of the transactions will be set to the unique ID
     * and currency of the account respectively
     *
     * @param transactionsList List of [Transaction]s to be set.
     */
    fun setTransactions(transactionsList: MutableList<Transaction>) {
        _transactionsList = transactionsList
    }

    /**
     * Returns a list of transactions for this account
     *
     * @return Array list of transactions for the account
     */
    val transactions: List<Transaction>
        get() = _transactionsList

    /**
     * Returns the number of transactions in this account
     *
     * @return Number transactions in account
     */
    val transactionCount: Int
        get() = _transactionsList.size

    /**
     * Returns the aggregate of all transactions in this account.
     * It takes into account debit and credit amounts, it does not however consider sub-accounts
     *
     * @return [Money] aggregate amount of all transactions in account.
     */
    val balance: Money
        get() {
            var balance = Money.createZeroInstance(_commodity!!.currencyCode)
            for (transaction in _transactionsList) {
                balance = balance.plus(transaction.getBalance(uID!!))
            }
            return balance
        }

    /**
     * The color of the account.
     */
    var color: Int
        get() = _color
        /**
         * Sets the color of the account.
         *
         * @param color Color as an int as returned by [Color].
         * @throws java.lang.IllegalArgumentException if the color is transparent,
         * which is not supported.
         */
        set(color) {
            require(Color.alpha(color) >= 255) { "Transparent colors are not supported: $color" }
            _color = color
        }

    /**
     * Returns the account color as an RGB hex string
     *
     * @return Hex color of the account
     */
    val colorHexString: String
        get() = String.format("#%06X", 0xFFFFFF and _color)

    /**
     * Sets the color of the account.
     *
     * @param colorCode Color code to be set in the format #rrggbb
     * @throws java.lang.IllegalArgumentException if the color code is not properly formatted or
     * the color is transparent.
     */
    //TODO: Allow use of #aarrggbb format as well
    fun setColor(colorCode: String) {
        color = Color.parseColor(colorCode)
    }

    //todo: should we also change commodity of transactions? Transactions can have splits from different accounts
    /**
     * The commodity for this account
     */
    var commodity: Commodity
        get() = _commodity!!
        set(value) {
            _commodity = value
        }

    /**
     * Sets the Unique Account Identifier of the parent account
     *
     * @param parentUID String Unique ID of parent account
     */
    fun setParentUID(parentUID: String?) {
        _parentAccountUID = parentUID
    }

    /**
     * Returns the Unique Account Identifier of the parent account
     *
     * @return String Unique ID of parent account
     */
    fun getParentUID(): String? {
        return _parentAccountUID
    }

    /**
     * Returns `true` if this account is a placeholder account, `false` otherwise.
     *
     * @return `true` if this account is a placeholder account, `false` otherwise
     */
    fun isPlaceholderAccount(): Boolean {
        return _isPlaceholderAccount
    }

    /**
     * Returns the hidden property of this account.
     *
     * Hidden accounts are not visible in the UI
     *
     * @return `true` if the account is hidden, `false` otherwise.
     */
    fun isHidden(): Boolean {
        return _isHidden
    }

    /**
     * Toggles the hidden property of the account.
     *
     * Hidden accounts are not visible in the UI
     *
     * @param hidden boolean specifying is hidden or not
     */
    fun setHidden(hidden: Boolean) {
        _isHidden = hidden
    }

    /**
     * Sets the placeholder flag for this account.
     * Placeholder accounts cannot have transactions
     *
     * @param isPlaceholder Boolean flag indicating if the account is a placeholder account or not
     */
    fun setPlaceHolderFlag(isPlaceholder: Boolean) {
        _isPlaceholderAccount = isPlaceholder
    }

    /**
     * Return the unique ID of accounts to which to default transfer transactions to
     *
     * @return Unique ID string of default transfer account
     */
    fun getDefaultTransferAccountUID(): String? {
        return _defaultTransferAccountUID
    }

    /**
     * Set the unique ID of account which is the default transfer target
     *
     * @param defaultTransferAccountUID Unique ID string of default transfer account
     */
    fun setDefaultTransferAccountUID(defaultTransferAccountUID: String?) {
        _defaultTransferAccountUID = defaultTransferAccountUID
    }

    /**
     * Converts this account's transactions into XML and adds them to the DOM document
     *
     * @param doc             XML DOM document for the OFX data
     * @param parent          Parent node to which to add this account's transactions in XML
     * @param exportStartTime Time from which to export transactions which are created/modified after
     */
    fun toOfx(doc: Document, parent: Element, exportStartTime: Timestamp?) {
        val currency = doc.createElement(OfxHelper.TAG_CURRENCY_DEF)
        currency.appendChild(doc.createTextNode(_commodity!!.currencyCode))

        //================= BEGIN BANK ACCOUNT INFO (BANKACCTFROM) =================================
        val bankId = doc.createElement(OfxHelper.TAG_BANK_ID)
        bankId.appendChild(doc.createTextNode(OfxHelper.APP_ID))
        val acctId = doc.createElement(OfxHelper.TAG_ACCOUNT_ID)
        acctId.appendChild(doc.createTextNode(uID))
        val accttype = doc.createElement(OfxHelper.TAG_ACCOUNT_TYPE)
        val ofxAccountType = convertToOfxAccountType(
            accountType
        ).toString()
        accttype.appendChild(doc.createTextNode(ofxAccountType))
        val bankFrom = doc.createElement(OfxHelper.TAG_BANK_ACCOUNT_FROM)
        bankFrom.appendChild(bankId)
        bankFrom.appendChild(acctId)
        bankFrom.appendChild(accttype)

        //================= END BANK ACCOUNT INFO ============================================


        //================= BEGIN ACCOUNT BALANCE INFO =================================
        val balance = balance.toPlainString()
        val formattedCurrentTimeString = OfxHelper.getFormattedCurrentTime()
        val balanceAmount = doc.createElement(OfxHelper.TAG_BALANCE_AMOUNT)
        balanceAmount.appendChild(doc.createTextNode(balance))
        val dtasof = doc.createElement(OfxHelper.TAG_DATE_AS_OF)
        dtasof.appendChild(doc.createTextNode(formattedCurrentTimeString))
        val ledgerBalance = doc.createElement(OfxHelper.TAG_LEDGER_BALANCE)
        ledgerBalance.appendChild(balanceAmount)
        ledgerBalance.appendChild(dtasof)

        //================= END ACCOUNT BALANCE INFO =================================


        //================= BEGIN TIME PERIOD INFO =================================
        val dtstart = doc.createElement(OfxHelper.TAG_DATE_START)
        dtstart.appendChild(doc.createTextNode(formattedCurrentTimeString))
        val dtend = doc.createElement(OfxHelper.TAG_DATE_END)
        dtend.appendChild(doc.createTextNode(formattedCurrentTimeString))

        //================= END TIME PERIOD INFO =================================


        //================= BEGIN TRANSACTIONS LIST =================================
        val bankTransactionsList = doc.createElement(OfxHelper.TAG_BANK_TRANSACTION_LIST)
        bankTransactionsList.appendChild(dtstart)
        bankTransactionsList.appendChild(dtend)
        for (transaction in _transactionsList) {
            if (transaction.modifiedTimestamp.before(exportStartTime)) continue
            bankTransactionsList.appendChild(transaction.toOFX(doc, uID!!))
        }
        //================= END TRANSACTIONS LIST =================================
        val statementTransactions = doc.createElement(OfxHelper.TAG_STATEMENT_TRANSACTIONS)
        statementTransactions.appendChild(currency)
        statementTransactions.appendChild(bankFrom)
        statementTransactions.appendChild(bankTransactionsList)
        statementTransactions.appendChild(ledgerBalance)
        parent.appendChild(statementTransactions)
    }

    companion object {
        /**
         * The MIME type for accounts in GnucashMobile
         * This is used when sending intents from third-party applications
         */
        const val MIME_TYPE =
            "vnd.android.cursor.item/vnd." + BuildConfig.APPLICATION_ID + ".account"

        /**
         * Default color, if not set explicitly through [.setColor].
         */
        // TODO: get it from a theme value?
        const val DEFAULT_COLOR = Color.LTGRAY

        /**
         * An extra key for passing the currency code (according ISO 4217) in an intent
         */
        const val EXTRA_CURRENCY_CODE = "org.gnucash.android.extra.currency_code"

        /**
         * Extra key for passing the unique ID of the parent account when creating a
         * new account using Intents
         */
        const val EXTRA_PARENT_UID = "org.gnucash.android.extra.parent_uid"

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
        fun convertToOfxAccountType(accountType: AccountType?): OfxAccountType {
            return when (accountType) {
                AccountType.CREDIT, AccountType.LIABILITY -> OfxAccountType.CREDITLINE
                AccountType.CASH, AccountType.INCOME, AccountType.EXPENSE, AccountType.PAYABLE, AccountType.RECEIVABLE -> OfxAccountType.CHECKING
                AccountType.BANK, AccountType.ASSET -> OfxAccountType.SAVINGS
                AccountType.MUTUAL, AccountType.STOCK, AccountType.EQUITY, AccountType.CURRENCY -> OfxAccountType.MONEYMRKT
                else -> OfxAccountType.CHECKING
            }
        }
    }
}