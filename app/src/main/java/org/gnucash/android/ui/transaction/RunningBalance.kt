/*
Copyright (c) 2026 dvsmax <244155368+dvsmax@users.noreply.github.com>
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package org.gnucash.android.ui.transaction

import org.gnucash.android.model.Money
import org.gnucash.android.util.NEVER

/**
 * "Beginning of time": any real timestamp, including negative (pre-1970) dates.
 * Deliberately distinct from the ALWAYS/NEVER sentinels (both Long.MIN_VALUE)
 * so that AccountsDbAdapter.computeBalance takes the computed path instead of
 * reading the cached COLUMN_BALANCE.
 * See the PR #508 discussion.
 */
const val EARLIEST_TIMESTAMP = NEVER + 1

/**
 * Applies the account's normal-balance sign convention to per-transaction
 * signed amounts (DEBIT positive, CREDIT negative): on credit-normal accounts
 * (LIABILITY, INCOME, ...) the signs are flipped.
 * Note: the anchor returned by getAccountBalance already follows this
 * convention, so only the per-transaction amounts need this adjustment.
 */
fun applyNormalBalanceSign(
    amounts: Map<String, Money>,
    hasDebitNormalBalance: Boolean
): Map<String, Money> =
    if (hasDebitNormalBalance) amounts else amounts.mapValues { -it.value }

/**
 * Computes the running balance displayed next to each transaction.
 *
 * The algorithm walks the account's unfiltered transaction list newest first,
 * starting from the current account balance ([anchor]): each transaction is
 * assigned the balance as of immediately after its own posting, then its
 * signed amount is subtracted to recover the balance preceding it, which the
 * next (older) transaction receives. This avoids re-summing all older
 * transactions per row (O(n) instead of O(n^2)).
 *
 * @param transactionsDesc (transactionUID, signed amount) pairs in
 *        normal-balance convention, newest first; see [applyNormalBalanceSign].
 * @param anchor account balance over all of its own transactions, excluding
 *        sub-accounts; must be computed with [EARLIEST_TIMESTAMP] to bypass
 *        the balance cache.
 * @return map of transactionUID to the account balance immediately after
 *         that transaction.
 */
fun computeRunningBalances(
    transactionsDesc: List<Pair<String, Money>>,
    anchor: Money
): Map<String, Money> {
    val result = HashMap<String, Money>(transactionsDesc.size * 2)
    var balance = anchor
    for ((uid, amount) in transactionsDesc) {
        result[uid] = balance
        balance -= amount
    }
    return result
}
