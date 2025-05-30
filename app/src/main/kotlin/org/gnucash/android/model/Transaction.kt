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

import android.content.Intent
import org.gnucash.android.BuildConfig
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.util.formatShortDate
import java.util.Date

/**
 * Represents a financial transaction, either credit or debit.
 * Transactions belong to accounts and each have the unique identifier of the account to which they belong.
 * The default type is a debit, unless otherwise specified.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class Transaction : BaseModel {
    /**
     * GUID of commodity associated with this transaction
     */
    private var _commodity: Commodity? = null

    /**
     * The splits making up this transaction
     */
    private var _splitList = mutableListOf<Split>()

    /**
     * An extra note giving details about the transaction
     */
    var note: String? = ""

    /**
     * Flag indicating if this transaction has been exported before or not
     * The transactions are typically exported as bank statement in the OFX format
     */
    var isExported = false

    /**
     * Timestamp when this transaction occurred
     */
    var timeMillis: Long = 0
        private set

    /**
     * Flag indicating that this transaction is a template
     */
    var isTemplate = false

    /**
     * GUID of [ScheduledAction] which created this transaction
     */
    var scheduledActionUID: String? = null

    /**
     * Overloaded constructor. Creates a new transaction instance with the
     * provided data and initializes the rest to default values.
     *
     * @param name Name of the transaction
     */
    constructor(name: String?) {
        initDefaults()
        description = name
    }

    /**
     * Copy constructor.
     * Creates a new transaction object which is a clone of the parameter.
     *
     * **Note:** The unique ID of the transaction is not cloned if the parameter `generateNewUID`,
     * is set to false. Otherwise, a new one is generated.<br />
     * The export flag and the template flag are not copied from the old transaction to the new.
     *
     * @param transaction    Transaction to be cloned
     * @param generateNewUID Flag to determine if new UID should be assigned or not
     */
    constructor(transaction: Transaction, generateNewUID: Boolean) {
        initDefaults()
        description = transaction.description
        note = transaction.note
        setTime(transaction.timeMillis)
        commodity = transaction.commodity
        //exported flag is left at default value of false
        for (split in transaction._splitList) {
            addSplit(Split(split, generateNewUID))
        }
        if (!generateNewUID) {
            setUID(transaction.uid)
        }
    }

    /**
     * Initializes the different fields to their default values.
     */
    private fun initDefaults() {
        commodity = Commodity.DEFAULT_COMMODITY
        timeMillis = System.currentTimeMillis()
    }

    /**
     * Creates a split which will balance the transaction, in value.
     *
     * **Note:**If a transaction has splits with different currencies, no auto-balancing will be performed.
     *
     *
     * The added split will not use any account in db, but will use currency code as account UID.
     * The added split will be returned, to be filled with proper account UID later.
     *
     * @return Split whose amount is the imbalance of this transaction
     */
    fun createAutoBalanceSplit(): Split? {
        val imbalance = imbalance //returns imbalance of 0 for multi-currency transactions
        if (!imbalance.isAmountZero) {
            // yes, this is on purpose the account UID is set to the currency.
            // This should be overridden before saving to db
            val split = Split(imbalance, accountUID = commodity.uid)
            split.type = if (imbalance.isNegative) TransactionType.CREDIT else TransactionType.DEBIT
            addSplit(split)
            return split
        }
        return null
    }

    /**
     * The GUID of the transaction
     * If the transaction has Splits, their transactionGUID will be updated as well
     */
    override fun setUID(uid: String?) {
        super.setUID(uid)
        for (split in _splitList) {
            split.transactionUID = uid
        }
    }

    /**
     * Returns list of splits for this transaction
     *
     * @return [java.util.List] of splits in the transaction
     */
    val splits: List<Split>
        get() = _splitList

    /**
     * Returns the list of splits belonging to a specific account
     *
     * @param accountUID Unique Identifier of the account
     * @return List of [org.gnucash.android.model.Split]s
     */
    fun getSplits(accountUID: String): List<Split> {
        val splits = mutableListOf<Split>()
        for (split in _splitList) {
            if (split.accountUID == accountUID) {
                splits.add(split)
            }
        }
        return splits
    }

    /**
     * Sets the splits for this transaction
     *
     * All the splits in the list will have their transaction UID set to this transaction
     *
     * @param splitList List of splits for this transaction
     */
    fun setSplits(splitList: MutableList<Split>) {
        _splitList = splitList
        for (split in splitList) {
            split.transactionUID = uid
        }
    }

    /**
     * Add a split to the transaction.
     *
     * Sets the split UID and currency to that of this transaction
     *
     * @param split Split for this transaction
     */
    fun addSplit(split: Split) {
        //sets the currency of the split to the currency of the transaction
        split.transactionUID = uid
        _splitList.add(split)
    }

    /**
     * Returns the balance of this transaction for only those splits which relate to the account.
     *
     * Uses a call to [.getBalance] with the appropriate parameters
     *
     * @param accountUID Unique Identifier of the account
     * @return Money balance of the transaction for the specified account
     * @see computeBalance
     */
    fun getBalance(accountUID: String): Money {
        return computeBalance(accountUID, _splitList)
    }

    /**
     * Returns the balance of this transaction for only those splits which relate to the account.
     *
     * Uses a call to [.getBalance] with the appropriate parameters
     *
     * @param account The account
     * @return Money balance of the transaction for the specified account
     * @see computeBalance
     */
    fun getBalance(account: Account): Money {
        return computeBalance(account, _splitList)
    }

    /**
     * Computes the imbalance amount for the given transaction.
     * In double entry, all transactions should resolve to zero. But imbalance occurs when there are unresolved splits.
     *
     * **Note:** If this is a multi-currency transaction, an imbalance of zero will be returned
     *
     * @return Money imbalance of the transaction or zero if it is a multi-currency transaction
     */
    private val imbalance: Money
        get() {
            val commodity = this.commodity
            var imbalance = Money.createZeroInstance(commodity)
            for (split in splits) {
                if (split.quantity.commodity != commodity) {
                    // this may happen when importing XML exported from GNCA before 2.0.0
                    // these transactions should only be imported from XML exported from GNC desktop
                    // so imbalance split should not be generated for them
                    return Money.createZeroInstance(commodity)
                }
                val amount = split.value
                if (amount.commodity != commodity) {
                    return Money.createZeroInstance(commodity)
                }
                imbalance = if (split.type === TransactionType.DEBIT) {
                    imbalance - amount
                } else {
                    imbalance + amount
                }
            }
            return imbalance
        }

    /**
     * Returns the currency code of this transaction.
     *
     * @return ISO 4217 currency code string
     */
    val currencyCode: String
        get() = commodity.currencyCode

    /**
     * The commodity for this transaction
     */
    var commodity: Commodity
        get() = _commodity!!
        set(commodity) {
            _commodity = commodity
        }

    /**
     * A description of the transaction
     */
    var description: String? = ""
        set(value) {
            field = value?.trim { it <= ' ' }.orEmpty()
        }

    /**
     * Set the time of the transaction
     *
     * @param timestamp Time when transaction occurred as [Date]
     */
    fun setTime(timestamp: Date) {
        timeMillis = timestamp.time
    }

    /**
     * Sets the time when the transaction occurred
     *
     * @param timeInMillis Time in milliseconds
     */
    fun setTime(timeInMillis: Long) {
        timeMillis = timeInMillis
    }

    override fun toString(): String {
        return "{description: $description, date: ${formatShortDate(timeMillis)}}"
    }

    companion object {
        /**
         * Mime type for transactions in GnuCash.
         * Used for recording transactions through intents
         */
        const val MIME_TYPE =
            "vnd.android.cursor.item/vnd." + BuildConfig.APPLICATION_ID + ".transaction"

        /**
         * Key for passing the account unique Identifier as an argument through an [Intent]
         *
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_ACCOUNT_UID = "${BuildConfig.APPLICATION_ID}.extra.account_uid"

        /**
         * Key for specifying the double entry account
         *
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_DOUBLE_ACCOUNT_UID =
            "${BuildConfig.APPLICATION_ID}.extra.double_account_uid"

        /**
         * Key for identifying the amount of the transaction through an Intent
         *
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_AMOUNT = "${BuildConfig.APPLICATION_ID}.extra.amount"

        /**
         * Extra key for the transaction type.
         * This value should typically be set by calling [TransactionType.name]
         *
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_TRANSACTION_TYPE = "${BuildConfig.APPLICATION_ID}.extra.transaction_type"

        /**
         * Argument key for passing splits as comma-separated multi-line list and each line is a split.
         * The line format is: <type>;<amount>;<account_uid>
         * The amount should be formatted in the US Locale
         */
        const val EXTRA_SPLITS = "${BuildConfig.APPLICATION_ID}.extra.transaction.splits"

        /**
         * Computes the balance of the splits belonging to a particular account.
         *
         * Only those splits which belong to the account will be considered.
         * If the `accountUID` is null, then the imbalance of the transaction is computed. This means that either
         * zero is returned (for balanced transactions) or the imbalance amount will be returned.
         *
         * @param accountUID Unique Identifier of the account
         * @param splits  List of splits
         * @return Money list of splits
         */
        @JvmStatic
        fun computeBalance(accountUID: String, splits: List<Split>): Money {
            val accountsDbAdapter = AccountsDbAdapter.getInstance()
            val account = accountsDbAdapter.getSimpleRecord(accountUID)!!
            return computeBalance(account, splits)
        }

        /**
         * Computes the balance of the splits belonging to a particular account.
         *
         * Only those splits which belong to the account will be considered.
         * If the `accountUID` is null, then the imbalance of the transaction is computed. This means that either
         * zero is returned (for balanced transactions) or the imbalance amount will be returned.
         *
         * @param account The account
         * @param splits  List of splits
         * @return Money list of splits
         */
        @JvmStatic
        fun computeBalance(account: Account, splits: List<Split>): Money {
            val accountUID = account.uid
            val accountType = account.accountType
            val accountCommodity = account.commodity
            val isDebitAccount = accountType.hasDebitNormalBalance
            var balance = Money.createZeroInstance(accountCommodity)
            for (split in splits) {
                if (split.accountUID != accountUID) continue
                val amount: Money = if (split.value.commodity == accountCommodity) {
                    split.value
                } else { //if this split belongs to the account, then either its value or quantity is in the account currency
                    split.quantity
                }
                val isDebitSplit = split.type === TransactionType.DEBIT
                balance =
                    if ((isDebitAccount && isDebitSplit) || (!isDebitAccount && !isDebitSplit)) {
                        balance + amount
                    } else {
                        balance - amount
                    }
            }
            return balance
        }

        /**
         * Returns the corresponding [TransactionType] given the accounttype and the effect which the transaction
         * type should have on the account balance
         *
         * @param accountType         Type of account
         * @param shouldReduceBalance `true` if type should reduce balance, `false` otherwise
         * @return TransactionType for the account
         */
        @JvmStatic
        fun getTypeForBalance(
            accountType: AccountType,
            shouldReduceBalance: Boolean
        ): TransactionType {
            val type: TransactionType = if (accountType.hasDebitNormalBalance) {
                if (shouldReduceBalance) TransactionType.CREDIT else TransactionType.DEBIT
            } else {
                if (shouldReduceBalance) TransactionType.DEBIT else TransactionType.CREDIT
            }
            return type
        }

        /**
         * Returns true if the transaction type represents a decrease for the account balance for the `accountType`, false otherwise
         *
         * @return true if the amount represents a decrease in the account balance, false otherwise
         * @see getTypeForBalance
         */
        @JvmStatic
        fun shouldDecreaseBalance(
            accountType: AccountType,
            transactionType: TransactionType
        ): Boolean {
            return if (accountType.hasDebitNormalBalance) {
                transactionType === TransactionType.CREDIT
            } else transactionType === TransactionType.DEBIT
        }

        /**
         * Creates an Intent with arguments from the `transaction`.
         * This intent can be broadcast to create a new transaction
         *
         * @param transaction Transaction used to create intent
         * @return Intent with transaction details as extras
         */
        @JvmStatic
        fun createIntent(transaction: Transaction): Intent {
            val intent = Intent(Intent.ACTION_INSERT)
            intent.type = MIME_TYPE
            intent.putExtra(Intent.EXTRA_TITLE, transaction.description)
            intent.putExtra(Intent.EXTRA_TEXT, transaction.note)
            intent.putExtra(Account.EXTRA_CURRENCY_CODE, transaction.currencyCode)
            val stringBuilder = StringBuilder()
            for (split in transaction.splits) {
                stringBuilder.append(split.toCsv()).append("\n")
            }
            intent.putExtra(EXTRA_SPLITS, stringBuilder.toString())
            return intent
        }
    }
}
