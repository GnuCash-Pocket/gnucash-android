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
package org.gnucash.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.gnucash.android.app.getSerializableCompat
import org.gnucash.android.app.isNullOrEmpty
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.csv.CsvTransactionsExporter.Companion.parseSplit
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Broadcast receiver responsible for creating transactions received through [Intent]s
 * In order to create a transaction through Intents, broadcast an intent with the arguments needed to
 * create the transaction. Transactions are strongly bound to [Account]s and it is recommended to
 * create an Account for your transaction splits.
 *
 * Remember to declare the appropriate permissions in order to create transactions with Intents.
 * The required permission is "org.gnucash.android.permission.RECORD_TRANSACTION"
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see AccountCreator
 *
 * @see Transaction.createIntent
 */
class TransactionRecorder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("Received transaction recording intent")
        val args = intent.extras
        if (args.isNullOrEmpty()) {
            Timber.w("Transaction arguments required")
            return
        }

        val name = args.getString(Intent.EXTRA_TITLE)
        if (name.isNullOrEmpty()) {
            Timber.w("Transaction name required")
            return
        }
        val transactionsDbAdapter = TransactionsDbAdapter.instance
        val commoditiesDbAdapter = transactionsDbAdapter.commoditiesDbAdapter

        val notes = args.getString(Intent.EXTRA_TEXT)

        val currencyUID = args.getString(Account.EXTRA_CURRENCY_UID)
        val commodity: Commodity?
        if (currencyUID.isNullOrEmpty()) {
            val currencyCode = args.getString(Account.EXTRA_CURRENCY_CODE)
            commodity = commoditiesDbAdapter.getCurrency(currencyCode)
        } else {
            commodity = commoditiesDbAdapter.getRecord(currencyUID)
        }
        if (commodity == null) {
            Timber.w("Commodity required")
            return
        }

        val transaction = Transaction(name)
        transaction.datePosted = System.currentTimeMillis()
        transaction.notes = notes.orEmpty()
        transaction.commodity = commodity

        //Parse deprecated args for compatibility. Transactions were bound to accounts, now only splits are
        val accountUID = args.getString(Transaction.EXTRA_ACCOUNT_UID)
        if (accountUID != null) {
            val type = TransactionType.of(args.getString(Transaction.EXTRA_TRANSACTION_TYPE)!!)
            var amountBigDecimal = args.getSerializableCompat(Transaction.EXTRA_AMOUNT, BigDecimal::class.java)!!
            amountBigDecimal =
                amountBigDecimal.setScale(commodity.smallestFractionDigits, RoundingMode.HALF_EVEN)
                    .round(MathContext.DECIMAL128)
            val amount = Money(amountBigDecimal, commodity)
            val split = Split(amount, accountUID)
            split.type = type
            transaction.addSplit(split)

            val transferAccountUID = args.getString(Transaction.EXTRA_DOUBLE_ACCOUNT_UID)
            if (!transferAccountUID.isNullOrEmpty()) {
                transaction.addSplit(split.createPair(transferAccountUID))
            }
        }

        val splits = args.getString(Transaction.EXTRA_SPLITS)
        if (splits != null) {
            val stringReader = StringReader(splits)
            val bufferedReader = BufferedReader(stringReader)
            var line: String?
            try {
                line = bufferedReader.readLine()
                while (line != null) {
                    val split = parseSplit(line)
                    transaction.addSplit(split)
                    line = bufferedReader.readLine()
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }

        transactionsDbAdapter.insert(transaction)

        WidgetConfigurationActivity.updateAllWidgets(context)
    }
}
