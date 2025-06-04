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
import androidx.annotation.ColorInt
import org.gnucash.android.BuildConfig
import org.gnucash.android.util.NotSet
import org.gnucash.android.util.formatHexRGB
import org.gnucash.android.util.parseColor

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
     * Name of this account
     */
    var name: String = ""
        set(value) {
            field = value.trim { it <= ' ' }
        }

    /**
     * Fully qualified name of this account including the parent hierarchy.
     * On instantiation of an account, the full name is set to the name by default
     */
    var fullName: String?

    /**
     * Account description
     */
    var description: String? = ""

    /**
     * Type of account
     * Defaults to [AccountType.CASH]
     */
    var accountType = AccountType.CASH

    /**
     * List of transactions in this account
     */
    private var _transactions = mutableListOf<Transaction>()

    /**
     * Account UID of the parent account. Can be null
     */
    var parentUID: String? = null

    /**
     * Save UID of a default account for transfers.
     * All transactions in this account will by default be transfers to the other account
     */
    var defaultTransferAccountUID: String? = null

    /**
     * Flag for placeholder accounts.
     * These accounts cannot have transactions
     */
    var isPlaceholder = false

    /**
     * `true` if this account is flagged as a favorite account, `false` if not
     */
    var isFavorite = false

    /**
     * Flag which indicates if this account is a hidden account or not
     */
    var isHidden = false

    val isRoot: Boolean get() = accountType == AccountType.ROOT

    val isTemplate: Boolean get() = commodity.isTemplate

    /**
     * Overloaded constructor
     *
     * @param name      Name of the account
     * @param commodity [Commodity] to be used by transactions in this account
     */
    @JvmOverloads
    constructor(name: String, commodity: Commodity = Commodity.DEFAULT_COMMODITY) {
        this.name = name
        fullName = this.name
        this.commodity = commodity
    }

    /**
     * Adds a transaction to this account
     *
     * @param transaction [Transaction] to be added to the account
     */
    fun addTransaction(transaction: Transaction) {
        transaction.commodity = commodity
        _transactions.add(transaction)
    }

    /**
     * Returns a list of transactions for this account
     *
     * @return Array list of transactions for the account
     */
    var transactions: List<Transaction>
        get() = _transactions
        /**
         * Sets a list of transactions for this account.
         * Overrides any previous transactions with those in the list.
         * The account UID and currency of the transactions will be set to the unique ID
         * and currency of the account respectively
         *
         * @param value List of [Transaction]s to be set.
         */
        set(value) {
            _transactions = value.toMutableList()
        }

    /**
     * Returns the number of transactions in this account
     *
     * @return Number transactions in account
     */
    val transactionCount: Int
        get() = _transactions.size

    /**
     * The color of the account.
     */
    @ColorInt
    var color: Int = DEFAULT_COLOR
        /**
         * Sets the opaque color of the account.
         *
         * @param value Color as an int as returned by [Color].
         */
        set(value) {
            field = (value and maskRGB) or maskOpaque
        }

    /**
     * Returns the account color as an RGB hex string
     *
     * @return Hex color of the account - `null` if default color.
     */
    val colorHexString: String?
        get() = if (color != DEFAULT_COLOR) color.formatHexRGB() else null

    /**
     * Sets the color of the account.
     *
     * @param colorCode Color code to be set in the format #rrggbb
     * the color is transparent.
     */
    fun setColor(colorCode: String?) {
        if (colorCode.isNullOrEmpty() || colorCode == NotSet) {
            color = DEFAULT_COLOR
            return
        }
        color = parseColor(colorCode) ?: DEFAULT_COLOR
    }

    //todo: should we also change commodity of transactions? Transactions can have splits from different accounts
    /**
     * The commodity for this account
     */
    var commodity: Commodity = Commodity.DEFAULT_COMMODITY

    var note: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Account) return false
        return super.equals(other)
                && (this.name == other.name)
                && (this.isFavorite == other.isFavorite)
                && (this.isHidden == other.isHidden)
                && (this.isPlaceholder == other.isPlaceholder)
                && (this.commodity == other.commodity)
    }

    override fun toString(): String = fullName ?: name

    companion object {
        /**
         * The MIME type for accounts in GnucashMobile
         * This is used when sending intents from third-party applications
         */
        const val MIME_TYPE =
            "vnd.android.cursor.item/vnd." + BuildConfig.APPLICATION_ID + ".account"

        /**
         * Default color, if not set explicitly through [.setColor].
         * @see https://github.com/Gnucash/gnucash/blob/stable/gnucash/gnome-utils/dialog-account.c
         */
        // TODO: get it from a theme value?
        @ColorInt
        @JvmField
        val DEFAULT_COLOR = Color.rgb(237, 236, 235)

        /**
         * An extra key for passing the currency code (according ISO 4217) in an intent
         */
        const val EXTRA_CURRENCY_CODE = "${BuildConfig.APPLICATION_ID}.extra.currency_code"

        /**
         * An extra key for passing the currency UID in an intent
         */
        const val EXTRA_CURRENCY_UID = "${BuildConfig.APPLICATION_ID}.extra.currency_uid"

        /**
         * Extra key for passing the unique ID of the parent account when creating a
         * new account using Intents
         */
        const val EXTRA_PARENT_UID = "${BuildConfig.APPLICATION_ID}.extra.parent_uid"

        private const val maskRGB: Int = 0xFFFFFF
        private const val maskOpaque: Int = 0xFF000000.toInt()
    }
}