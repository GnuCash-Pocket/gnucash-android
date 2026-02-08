/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.ui.util

import android.os.AsyncTask
import android.widget.TextView
import androidx.annotation.ColorInt
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.Money
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * An asynchronous task for computing the account balance of an account.
 * This is done asynchronously because in cases of deeply nested accounts,
 * it can take some time and would block the UI thread otherwise.
 */
class AccountBalanceTask(
    private val accountsDbAdapter: AccountsDbAdapter,
    balanceTextView: TextView?,
    @ColorInt private val colorBalanceZero: Int
) : AsyncTask<String, Any, Money>() {
    private val accountBalanceTextViewReference = WeakReference<TextView>(balanceTextView)

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: String): Money? {
        val accountUID = params[0]
        //if the view for which we are doing this job is dead, kill the job as well
        if (accountBalanceTextViewReference.get() == null) {
            cancel(true)
            return null
        }

        try {
            val account = accountsDbAdapter.getRecord(accountUID)
            val balance = accountsDbAdapter.getAccountBalance(account)
            val accountType = account.type
            return if (accountType.hasDebitNormalBalance != accountType.hasDebitDisplayBalance) {
                -balance
            } else {
                balance
            }
        } catch (e: Exception) {
            Timber.e(e, "Error computing account balance")
        }
        return null
    }

    override fun onPostExecute(balance: Money?) {
        val balanceTextView = accountBalanceTextViewReference.get()
        balanceTextView?.displayBalance(balance, colorBalanceZero)
    }
}
