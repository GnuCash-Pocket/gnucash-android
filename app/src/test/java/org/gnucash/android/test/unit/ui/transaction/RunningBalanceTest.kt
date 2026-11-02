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
package org.gnucash.android.test.unit.ui.transaction

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.ALWAYS
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.ui.transaction.EARLIEST_TIMESTAMP
import org.gnucash.android.ui.transaction.applyNormalBalanceSign
import org.gnucash.android.ui.transaction.computeRunningBalances
import org.gnucash.android.util.NEVER
import org.junit.Test

/**
 * Unit tests for the pure running-balance helpers in RunningBalance.kt.
 *
 * DB-level behaviour (SQL aggregation, sign conventions, denominators,
 * template exclusion, quantity vs value) is covered by AccountsDbAdapterTest
 * instead. These tests focus strictly on the mathematical correctness of
 * the backward-subtraction algorithm and the sign-flip convention.
 */
class RunningBalanceTest : GnuCashTest() {

    private fun money(value: String) = Money(value, Commodity.USD)

    @Test
    fun `running balance subtracts each amount from the anchor, newest first`() {
        val txs = listOf("t1" to money("30"), "t2" to money("-10"), "t3" to money("5"))
        val result = computeRunningBalances(txs, money("100"))
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf("t1" to money("100"), "t2" to money("70"), "t3" to money("80"))
        )
    }

    @Test
    fun `multiple transactions on the same day keep list order`() {
        val txs = listOf("a" to money("10"), "b" to money("10"), "c" to money("10"))
        val result = computeRunningBalances(txs, money("130"))
        assertThat(result["a"]).isEqualTo(money("130"))
        assertThat(result["b"]).isEqualTo(money("120"))
        assertThat(result["c"]).isEqualTo(money("110"))
    }

    @Test
    fun `empty list gives empty map`() {
        assertThat(computeRunningBalances(emptyList(), money("42"))).isEmpty()
    }

    @Test
    fun `EARLIEST_TIMESTAMP is distinct from the ALWAYS cache sentinel`() {
        assertThat(EARLIEST_TIMESTAMP).isNotEqualTo(ALWAYS)
        assertThat(EARLIEST_TIMESTAMP).isEqualTo(NEVER + 1)
    }

    @Test
    fun `applyNormalBalanceSign does not flip for debit-normal`() {
        val amounts = mapOf("t1" to money("10.00"))
        val result = applyNormalBalanceSign(amounts, hasDebitNormalBalance = true)
        assertThat(result["t1"]).isEqualTo(money("10.00"))
    }

    @Test
    fun `applyNormalBalanceSign flips for credit-normal`() {
        val amounts = mapOf("t1" to money("10.00"))
        val result = applyNormalBalanceSign(amounts, hasDebitNormalBalance = false)
        assertThat(result["t1"]).isEqualTo(money("-10.00"))
    }
}
