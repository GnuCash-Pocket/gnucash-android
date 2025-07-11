/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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
import android.os.Bundle;
import android.text.TextUtils;

import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Commodity;

import timber.log.Timber;

/**
 * Broadcast receiver responsible for creating {@link Account}s received through intents.
 * In order to create an <code>Account</code>, you need to broadcast an {@link Intent} with arguments
 * for the name, currency and optionally, a unique identifier for the account (which should be unique to GnuCash)
 * of the Account to be created. Also remember to set the right mime type so that Android can properly route the Intent
 * <b>Note</b> This Broadcast receiver requires the permission "org.gnucash.android.permission.CREATE_ACCOUNT"
 * in order to be able to use Intents to create accounts. So remember to declare it in your manifest
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see {@link Account#EXTRA_CURRENCY_UID}, {@link Account#MIME_TYPE} {@link Intent#EXTRA_TITLE}, {@link Intent#EXTRA_UID}
 */
public class AccountCreator extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.i("Received account creation intent");
        Bundle args = intent.getExtras();
        if (args == null) {
            Timber.w("Account arguments required");
            return;
        }

        String name = args.getString(Intent.EXTRA_TITLE);
        if (TextUtils.isEmpty(name)) {
            Timber.w("Account name required");
            return;
        }
        Account account = new Account(name);
        account.setParentUID(args.getString(Account.EXTRA_PARENT_UID));

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
        account.setCommodity(commodity);

        String uid = args.getString(Intent.EXTRA_UID);
        if (uid != null) {
            account.setUID(uid);
        }

        AccountsDbAdapter.getInstance().addRecord(account, DatabaseAdapter.UpdateMethod.insert);
    }

}
