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
package org.gnucash.android.test.unit.model

import android.graphics.Color
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Transaction
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.parseColor
import org.junit.Test

class AccountTest : GnuCashTest() {
    @Test
    fun testAccountUsesDefaultCurrency() {
        val account = Account("Dummy account")
        assertThat(account.commodity.currencyCode)
            .isEqualTo(Commodity.DEFAULT_COMMODITY.currencyCode)
    }

    @Test
    fun testAccountAlwaysHasUID() {
        val account = Account("Dummy")
        assertThat(account.uid).isNotNull()
    }

    @Test
    fun testTransactionsHaveSameCurrencyAsAccount() {
        val acc1 = Account("Japanese", Commodity.JPY)
        acc1.setUID("simile")
        val trx = Transaction("Underground")
        val term = Transaction("Tube")

        assertThat(trx.currencyCode).isEqualTo(Commodity.DEFAULT_COMMODITY.currencyCode)

        acc1.addTransaction(trx)
        acc1.addTransaction(term)

        assertThat(trx.currencyCode).isEqualTo("JPY")
        assertThat(term.currencyCode).isEqualTo("JPY")
    }

    @Test
    fun testSetInvalidColorCode() {
        val account = Account("Test")
        account.setColor("443859")
        assertThat(account.color).isEqualTo(Account.DEFAULT_COLOR)
    }

    @Test
    fun testSetColorWithAlphaComponent() {
        val account = Account("Test")
        account.color = parseColor("#aa112233")!!
        assertThat(account.color).isEqualTo(Color.rgb(0xaa, 0x11, 0x22))
    }

    @Test
    fun shouldSetFullNameWhenCreated() {
        val fullName = "Full name "
        val account = Account(fullName)
        assertThat(account.name)
            .isEqualTo(fullName.trim()) //names are trimmed
        assertThat(account.fullName)
            .isEqualTo(fullName.trim()) //names are trimmed
    }

    @Test
    fun settingNameShouldNotChangeFullName() {
        val fullName = "Full name"
        val account = Account(fullName)

        account.name = "Name"
        assertThat(account.name).isEqualTo("Name")
        assertThat(account.fullName).isEqualTo(fullName)
    }

    @Test
    fun newInstance_shouldReturnNonNullValues() {
        val account = Account("Test account")
        assertThat(account.description).isEqualTo("")
        assertThat(account.color).isEqualTo(Account.DEFAULT_COLOR)
    }

    @Test
    fun colors() {
        val account = Account("Name")
        assertThat(account.color).isEqualTo(Account.DEFAULT_COLOR)
        account.setColor("null")
        assertThat(account.color).isEqualTo(Account.DEFAULT_COLOR)
        account.setColor("blah")
        assertThat(account.color).isEqualTo(Account.DEFAULT_COLOR)
        account.setColor("aliceblue")
        assertThat(account.color).isEqualTo(Color.rgb(240, 248, 255))
        assertThat(account.colorHexString).isEqualTo("#F0F8FF")
        account.setColor("#0000ff")
        assertThat(account.color).isEqualTo(Color.BLUE)
        assertThat(account.colorHexString).isEqualTo("#0000FF")
        account.setColor("#fff")
        assertThat(account.color).isEqualTo(Color.WHITE)
        assertThat(account.colorHexString).isEqualTo("#FFFFFF")
        account.setColor("rgb(0,255,0)")
        assertThat(account.color).isEqualTo(Color.GREEN)
        assertThat(account.colorHexString).isEqualTo("#00FF00")
        account.setColor("rgba(255, 0, 0, 0.5)")
        assertThat(account.color).isEqualTo(Color.RED)
        assertThat(account.colorHexString).isEqualTo("#FF0000")
        account.setColor("hsl(300, 100%, 50%)")
        assertThat(account.color).isEqualTo(Color.MAGENTA)
        assertThat(account.colorHexString).isEqualTo("#FF00FF")
        account.setColor("hsla(0, 100%, 50%, 1.0)")
        assertThat(account.color).isEqualTo(Color.RED)
        assertThat(account.colorHexString).isEqualTo("#FF0000")
    }
}
