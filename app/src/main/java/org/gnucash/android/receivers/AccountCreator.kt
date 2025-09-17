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
package org.gnucash.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.gnucash.android.app.isNullOrEmpty
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import timber.log.Timber

/**
 * Broadcast receiver responsible for creating [Account]s received through intents.
 * In order to create an `Account`, you need to broadcast an [Intent] with arguments
 * for the name, currency and optionally, a unique identifier for the account (which should be unique to GnuCash)
 * of the Account to be created. Also remember to set the right mime type so that Android can properly route the Intent
 * **Note** This Broadcast receiver requires the permission "org.gnucash.android.permission.CREATE_ACCOUNT"
 * in order to be able to use Intents to create accounts. So remember to declare it in your manifest
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see {@link Account.EXTRA_CURRENCY_UID}, {@link Account.MIME_TYPE} {@link Intent.EXTRA_TITLE}, {@link Intent.EXTRA_UID}
 */
class AccountCreator : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("Received account creation intent")
        val args = intent.extras
        if (args.isNullOrEmpty()) {
            Timber.w("Account arguments required")
            return
        }
        val accountsDbAdapter = AccountsDbAdapter.instance
        val commoditiesDbAdapter = accountsDbAdapter.commoditiesDbAdapter

        val name = args.getString(Intent.EXTRA_TITLE)
        if (name.isNullOrEmpty()) {
            Timber.w("Account name required")
            return
        }
        val account = Account(name)
        account.parentUID = args.getString(Account.EXTRA_PARENT_UID)

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
        account.commodity = commodity

        val uid = args.getString(Intent.EXTRA_UID)
        if (uid != null) {
            account.setUID(uid)
        }

        accountsDbAdapter.insert(account)
    }
}
