package org.gnucash.android.model

import android.os.Parcel
import android.os.Parcelable
import java.lang.StringBuilder
import org.gnucash.android.db.adapter.AccountsDbAdapter
import java.sql.Timestamp

/**
 * A split amount in a transaction.
 *
 *
 * Every transaction is made up of at least two splits (representing a double
 * entry transaction)
 *
 *
 * Amounts are always stored unsigned. This is independent of the negative values
 * which are shown in the UI (for user convenience). The actual movement of the
 * balance in the account depends on the type of normal balance of the account
 * and the transaction type of the split (CREDIT/DEBIT).
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class Split : BaseModel, Parcelable {
    /**
     * Money amount of the split with the currency of the transaction.
     * @see .getQuantity
     */
    var value: Money? = null
        private set

    /**
     * Amount of the split in the currency of the account to which the split belongs
     */
    private var _quantity: Money? = null

    /**
     * Transaction UID which this split belongs to
     */
    var transactionUID: String? = ""

    /**
     * Account UID which this split belongs to
     */
    var accountUID: String? = null

    /**
     * The [TransactionType] of this transaction, credit or debit
     */
    var type: TransactionType? = TransactionType.CREDIT

    /**
     * Memo associated with this split
     */
    var memo: String? = null

    /**
     * The reconciled state is one of the following values:
     *
     *  * **y**: means this split has been reconciled
     *  * **n**: means this split is not reconciled
     *  * **c**: means split has been cleared, but not reconciled
     *
     * One of the following flags [.FLAG_RECONCILED], [.FLAG_NOT_RECONCILED], [.FLAG_CLEARED]
     */
    var reconcileState = FLAG_NOT_RECONCILED

    /**
     * Date of the reconciliation. Database required non-null field
     */
    var reconcileDate = Timestamp(System.currentTimeMillis())

    /**
     * Initialize split with a value and quantity amounts and the owning account
     *
     * The transaction type is set to CREDIT. The amounts are stored unsigned.
     *
     * @param value      Money value amount of this split in the currency of the transaction.
     * @param quantity   Money value amount of this split in the currency of the
     * owning account.
     * @param accountUID String UID of transfer account
     */
    constructor(value: Money, quantity: Money, accountUID: String?) {
        this.quantity = quantity
        setValue(value)
        this.accountUID = accountUID
    }

    /**
     * Initialize split with a value amount and the owning account
     *
     *
     * The transaction type is set to CREDIT. The amount is stored unsigned.
     *
     * @param amount     Money value amount of this split. Value is always in the
     * currency the owning transaction. This amount will be assigned
     * as both the value and the quantity of this split.
     * @param accountUID String UID of owning account
     */
    constructor(amount: Money, accountUID: String?) : this(amount, Money(amount), accountUID) {}

    /**
     * Clones the `sourceSplit` to create a new instance with same fields
     *
     * @param sourceSplit Split to be cloned
     * @param generateUID Determines if the clone should have a new UID or should
     * maintain the one from source
     */
    constructor(sourceSplit: Split, generateUID: Boolean) {
        memo = sourceSplit.memo
        accountUID = sourceSplit.accountUID
        type = sourceSplit.type
        transactionUID = sourceSplit.transactionUID
        value = Money(sourceSplit.value!!)
        _quantity = Money(sourceSplit._quantity!!)

        //todo: clone reconciled status
        if (generateUID) {
            generateUID()
        } else {
            uID = sourceSplit.uID
        }
    }

    /**
     * Sets the value amount of the split.
     *
     *
     * The value is in the currency of the containing transaction.
     * It's stored unsigned.
     *
     * @param value Money value of this split
     * @see .setQuantity
     */
    fun setValue(value: Money) {
        this.value = value.abs()
    }

    /**
     * The quantity is in the currency of the account to which the split is associated
     * @see .getValue
     */
    var quantity: Money?
        get() = _quantity
        set(quantity) {
            _quantity = quantity!!.abs()
        }

    /**
     * Creates a split which is a pair of this instance.
     * A pair split has all the same attributes except that the SplitType is inverted and it belongs
     * to another account.
     *
     * @param accountUID GUID of account
     * @return New split pair of current split
     * @see TransactionType.invert
     */
    fun createPair(accountUID: String?): Split {
        val pair = Split(value!!, accountUID)
        pair.type = type!!.invert()
        pair.memo = memo
        pair.transactionUID = transactionUID
        pair.quantity = _quantity
        return pair
    }

    /**
     * Checks is this `other` is a pair split of this.
     *
     * Two splits are considered a pair if they have the same amount and
     * opposite split types
     *
     * @param other the other split of the pair to be tested
     * @return whether the two splits are a pair
     */
    fun isPairOf(other: Split): Boolean {
        return value!!.equals(other.value) && type!!.invert() == other.type
    }

    /**
     * Returns the formatted amount (with or without negation sign) for the split value
     *
     * @return Money amount of value
     * @see .getFormattedAmount
     */
    val formattedValue: Money
        get() = getFormattedAmount(value, accountUID, type)

    /**
     * Returns the formatted amount (with or without negation sign) for the quantity
     *
     * @return Money amount of quantity
     * @see .getFormattedAmount
     */
    val formattedQuantity: Money
        get() = getFormattedAmount(_quantity, accountUID, type)

    /**
     * Check if this split is reconciled
     *
     * @return `true` if the split is reconciled, `false` otherwise
     */
    val isReconciled: Boolean
        get() = reconcileState == FLAG_RECONCILED

    override fun toString(): String {
        return type!!.name + " of " + value.toString() + " in account: " + accountUID
    }

    /**
     * Returns a string representation of the split which can be parsed again
     * using [org.gnucash.android.model.Split.parseSplit]
     *
     *
     * The string is formatted as:<br />
     * "&lt;uid&gt;;&lt;valueNum&gt;;&lt;valueDenom&gt;;&lt;valueCurrencyCode&gt;;&lt;quantityNum
     * &gt;;&lt;quantityDenom&gt;;&lt;quantityCurrencyCode&gt;;&lt;transaction_uid&gt;;&lt;
     * account_uid&gt;;&lt;type&gt;;&lt;memo&gt;"
     *
     * **Only the memo field is allowed to be null**
     *
     * @return the converted CSV string of this split
     */
    fun toCsv(): String {
        //TODO: add reconciled state and date
        val splitString = StringBuilder()
            .append(uID)
            .append(SEPARATOR_CSV).append(value!!.numerator)
            .append(SEPARATOR_CSV).append(value!!.denominator)
            .append(SEPARATOR_CSV).append(value!!.commodity.currencyCode)
            .append(SEPARATOR_CSV).append(_quantity!!.numerator)
            .append(SEPARATOR_CSV).append(_quantity!!.denominator)
            .append(SEPARATOR_CSV).append(_quantity!!.commodity.currencyCode)
            .append(SEPARATOR_CSV).append(transactionUID)
            .append(SEPARATOR_CSV).append(accountUID)
            .append(SEPARATOR_CSV).append(type!!.name)
        if (memo != null) {
            splitString.append(SEPARATOR_CSV).append(memo)
        }
        return splitString.toString()
    }

    /**
     * Two splits are considered equivalent if all the fields (excluding GUID
     * and timestamps - created, modified, reconciled) are equal.
     *
     *
     * Any two splits which are equal are also equivalent, but the reverse
     * is not true
     *
     *
     * The difference with to [.equals] is that the GUID of
     * the split is not considered. This is useful in cases where a new split
     * is generated for a transaction with the same properties, but a new GUID
     * is generated e.g. when editing a transaction and modifying the splits
     *
     * @param split Other split for which to test equivalence
     * @return `true` if both splits are equivalent, `false` otherwise
     */
    fun isEquivalentTo(split: Split): Boolean {
        if (this === split) return true
        if (super.equals(split)) return true
        if (reconcileState != split.reconcileState) return false
        if (!value!!.equals(split.value)) return false
        if (!_quantity!!.equals(split._quantity)) return false
        if (transactionUID != split.transactionUID) return false
        if (accountUID != split.accountUID) return false
        if (type !== split.type) return false
        return if (memo != null) memo == split.memo else split.memo == null
    }

    /**
     * Two splits are considered equal if all their properties excluding
     * timestamps (created, modified, reconciled) are equal.
     *
     * @param other Other split to compare for equality
     * @return `true` if this split is equal to `o`, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        if (!super.equals(other)) return false
        val split = other as Split
        if (reconcileState != split.reconcileState) return false
        if (!value!!.equals(split.value)) return false
        if (!_quantity!!.equals(split._quantity)) return false
        if (transactionUID != split.transactionUID) return false
        if (accountUID != split.accountUID) return false
        if (type !== split.type) return false
        return if (memo != null) memo == split.memo else split.memo == null
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + _quantity.hashCode()
        result = 31 * result + transactionUID.hashCode()
        result = 31 * result + accountUID.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + if (memo != null) memo.hashCode() else 0
        result = 31 * result + reconcileState.code
        return result
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(uID)
        dest.writeString(accountUID)
        dest.writeString(transactionUID)
        dest.writeString(type!!.name)

        dest.writeMoney(value!!, flags)
        dest.writeMoney(_quantity!!, flags)

        dest.writeString(memo.orEmpty())
        dest.writeString(reconcileState.toString())
        dest.writeString(reconcileDate.toString())
    }

    /**
     * Constructor for creating a Split object from a Parcel
     *
     * @param source Source parcel containing the split
     * @see .CREATOR
     */
    private constructor(source: Parcel) {
        uID = source.readString()
        accountUID = source.readString()
        transactionUID = source.readString()
        type = TransactionType.valueOf(source.readString()!!)

        value = source.readMoney()
        _quantity = source.readMoney()

        memo = source.readString()
        reconcileState = source.readString()!![0]
        reconcileDate = Timestamp.valueOf(source.readString())
    }

    companion object {
        /**
         * Flag indicating that the split has been reconciled
         */
        const val FLAG_RECONCILED = 'y'

        /**
         * Flag indicating that the split has not been reconciled
         */
        const val FLAG_NOT_RECONCILED = 'n'

        /**
         * Flag indicating that the split has been cleared, but not reconciled
         */
        const val FLAG_CLEARED = 'c'

        private const val SEPARATOR_CSV = ";"

        /**
         * Splits are saved as absolute values to the database, with no negative numbers.
         * The type of movement the split causes to the balance of an account determines
         * its sign, and that depends on the split type and the account type
         *
         * @param amount     Money amount to format
         * @param accountUID GUID of the account
         * @param splitType  Transaction type of the split
         * @return -`amount` if the amount would reduce the balance of
         * `account`, otherwise +`amount`
         */
        private fun getFormattedAmount(
            amount: Money?,
            accountUID: String?,
            splitType: TransactionType?
        ): Money {
            val isDebitAccount =
                AccountsDbAdapter.getInstance().getAccountType(accountUID!!).hasDebitNormalBalance()
            val absAmount = amount!!.abs()
            val isDebitSplit = splitType === TransactionType.DEBIT
            return if (isDebitAccount) {
                if (isDebitSplit) {
                    absAmount
                } else {
                    absAmount.unaryMinus()
                }
            } else {
                if (isDebitSplit) {
                    absAmount.unaryMinus()
                } else {
                    absAmount
                }
            }
        }

        /**
         * Parses a split which is in the format:<br/>
         * "<uid>;<valueNum>;<valueDenom>;<currency_code>;<quantityNum>;<quantityDenom>;
         * <currency_code>;<transaction_uid>;<account_uid>;<type>;<memo>".
         *
         * <p>Also supports parsing of the deprecated format
         * "<amount>;<currency_code>;<transaction_uid>;<account_uid>;<type>;<memo>".
         * The split input string is the same produced by the {@link Split#toCsv()} method.</p>
         *
         * @param splitCsvString String containing formatted split
         * @return Split instance parsed from the string
         */
        @JvmStatic
        fun parseSplit(splitCsvString: String): Split {
            //TODO: parse reconciled state and date
            val tokens =
                splitCsvString.split(SEPARATOR_CSV.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return if (tokens.size < 8) { //old format splits
                val amount = Money(tokens[0], tokens[1])
                val split = Split(amount, tokens[2])
                split.transactionUID = tokens[3]
                split.type = TransactionType.valueOf(tokens[4])
                if (tokens.size == 6) {
                    split.memo = tokens[5]
                }
                split
            } else {
                val valueNum = tokens[1].toLong()
                val valueDenom = tokens[2].toLong()
                val valueCurrencyCode = tokens[3]
                val quantityNum = tokens[4].toLong()
                val quantityDenom = tokens[5].toLong()
                val qtyCurrencyCode = tokens[6]
                val value = Money(valueNum, valueDenom, valueCurrencyCode)
                val quantity = Money(quantityNum, quantityDenom, qtyCurrencyCode)
                val split = Split(value, tokens[8])
                split.uID = tokens[0]
                split.quantity = quantity
                split.transactionUID = tokens[7]
                split.type = TransactionType.valueOf(tokens[9])
                if (tokens.size == 11) {
                    split.memo = tokens[10]
                }
                split
            }
        }

        /**
         * Creates new Parcels containing the information in this split during serialization
         */
        @JvmField
        val CREATOR: Parcelable.Creator<Split> = object : Parcelable.Creator<Split> {
            override fun createFromParcel(source: Parcel): Split {
                return Split(source)
            }

            override fun newArray(size: Int): Array<Split?> {
                return arrayOfNulls(size)
            }
        }
    }
}
