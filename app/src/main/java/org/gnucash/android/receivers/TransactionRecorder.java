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

package org.gnucash.android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import timber.log.Timber;

/**
 * Broadcast receiver responsible for creating transactions received through {@link Intent}s
 * In order to create a transaction through Intents, broadcast an intent with the arguments needed to
 * create the transaction. Transactions are strongly bound to {@link Account}s and it is recommended to
 * create an Account for your transaction splits.
 * <p>Remember to declare the appropriate permissions in order to create transactions with Intents.
 * The required permission is "org.gnucash.android.permission.RECORD_TRANSACTION"</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see AccountCreator
 * @see org.gnucash.android.model.Transaction#createIntent(org.gnucash.android.model.Transaction)
 */
public class TransactionRecorder extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.i("Received transaction recording intent");
        Bundle args = intent.getExtras();
        if (args == null) {
            Timber.w("Transaction arguments required");
            return;
        }

        String name = args.getString(Intent.EXTRA_TITLE);
        if (TextUtils.isEmpty(name)) {
            Timber.w("Transaction name required");
            return;
        }
        TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();
        CommoditiesDbAdapter commoditiesDbAdapter = transactionsDbAdapter.commoditiesDbAdapter;

        String note = args.getString(Intent.EXTRA_TEXT);

        String currencyUID = args.getString(Account.EXTRA_CURRENCY_UID);
        final Commodity commodity;
        if (TextUtils.isEmpty(currencyUID)) {
            String currencyCode = args.getString(Account.EXTRA_CURRENCY_CODE);
            commodity = CommoditiesDbAdapter.getInstance().getCommodity(currencyCode);
        } else {
            commodity = CommoditiesDbAdapter.getInstance().getRecord(currencyUID);
        }
        if (commodity == null) {
            Timber.w("Commodity required");
            return;
        }

        Transaction transaction = new Transaction(name);
        transaction.setTime(System.currentTimeMillis());
        transaction.setNote(note);
        transaction.setCommodity(commodity);

        //Parse deprecated args for compatibility. Transactions were bound to accounts, now only splits are
        String accountUID = args.getString(Transaction.EXTRA_ACCOUNT_UID);
        if (accountUID != null) {
            TransactionType type = TransactionType.valueOf(args.getString(Transaction.EXTRA_TRANSACTION_TYPE));
            BigDecimal amountBigDecimal;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                amountBigDecimal = args.getSerializable(Transaction.EXTRA_AMOUNT, BigDecimal.class);
            } else {
                amountBigDecimal = (BigDecimal) args.getSerializable(Transaction.EXTRA_AMOUNT);
            }
            amountBigDecimal = amountBigDecimal.setScale(commodity.getSmallestFractionDigits(), RoundingMode.HALF_EVEN).round(MathContext.DECIMAL128);
            Money amount = new Money(amountBigDecimal, commodity);
            Split split = new Split(amount, accountUID);
            split.setType(type);
            transaction.addSplit(split);

            String transferAccountUID = args.getString(Transaction.EXTRA_DOUBLE_ACCOUNT_UID);
            if (transferAccountUID != null) {
                transaction.addSplit(split.createPair(transferAccountUID));
            }
        }

        String splits = args.getString(Transaction.EXTRA_SPLITS);
        if (splits != null) {
            StringReader stringReader = new StringReader(splits);
            BufferedReader bufferedReader = new BufferedReader(stringReader);
            String line;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    Split split = Split.parseSplit(line);
                    transaction.addSplit(split);
                }
            } catch (IOException e) {
                Timber.e(e);
            }
        }

        transactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert);

        WidgetConfigurationActivity.updateAllWidgets(context);
    }

}
