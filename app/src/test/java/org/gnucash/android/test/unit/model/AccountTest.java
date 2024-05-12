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
package org.gnucash.android.test.unit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gnucash.android.util.ColorExtKt.parseColor;

import android.graphics.Color;

import org.gnucash.android.model.Account;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class AccountTest {

    @Test
    public void testAccountUsesDefaultCurrency() {
        Account account = new Account("Dummy account");
        assertThat(account.getCommodity().getCurrencyCode()).isEqualTo(Money.DEFAULT_CURRENCY_CODE);
    }

    @Test
    public void testAccountAlwaysHasUID() {
        Account account = new Account("Dummy");
        assertThat(account.getUID()).isNotNull();
    }

    @Test
    public void testTransactionsHaveSameCurrencyAsAccount() {
        Account acc1 = new Account("Japanese", Commodity.JPY);
        acc1.setUID("simile");
        Transaction trx = new Transaction("Underground");
        Transaction term = new Transaction("Tube");

        assertThat(trx.getCurrencyCode()).isEqualTo(Money.DEFAULT_CURRENCY_CODE);

        acc1.addTransaction(trx);
        acc1.addTransaction(term);

        assertThat(trx.getCurrencyCode()).isEqualTo("JPY");
        assertThat(term.getCurrencyCode()).isEqualTo("JPY");
    }

    @Test
    public void testSetInvalidColorCode() {
        Account account = new Account("Test");
        account.setColor("443859");
        assertThat(account.getColor()).isEqualTo(Account.DEFAULT_COLOR);
    }

    @Test
    public void testSetColorWithAlphaComponent() {
        Account account = new Account("Test");
        account.setColor(parseColor("#aa112233"));
        assertThat(account.getColor()).isEqualTo(Color.rgb(0xaa, 0x11, 0x22));
    }

    @Test
    public void shouldSetFullNameWhenCreated() {
        String fullName = "Full name ";
        Account account = new Account(fullName);
        assertThat(account.getName()).isEqualTo(fullName.trim()); //names are trimmed
        assertThat(account.getFullName()).isEqualTo(fullName.trim()); //names are trimmed
    }

    @Test
    public void settingNameShouldNotChangeFullName() {
        String fullName = "Full name";
        Account account = new Account(fullName);

        account.setName("Name");
        assertThat(account.getName()).isEqualTo("Name");
        assertThat(account.getFullName()).isEqualTo(fullName);
    }

    @Test
    public void newInstance_shouldReturnNonNullValues() {
        Account account = new Account("Test account");
        assertThat(account.getDescription()).isEqualTo("");
        assertThat(account.getColor()).isEqualTo(Account.DEFAULT_COLOR);
    }

    @Test
    public void colors() {
        Account account = new Account("Name");
        assertThat(account.getColor()).isEqualTo(Account.DEFAULT_COLOR);
        account.setColor("null");
        assertThat(account.getColor()).isEqualTo(Account.DEFAULT_COLOR);
        account.setColor("blah");
        assertThat(account.getColor()).isEqualTo(Account.DEFAULT_COLOR);
        account.setColor("aliceblue");
        assertThat(account.getColor()).isEqualTo(Color.rgb(240, 248, 255));
        assertThat(account.getColorHexString()).isEqualTo("#F0F8FF");
        account.setColor("#0000ff");
        assertThat(account.getColor()).isEqualTo(Color.BLUE);
        assertThat(account.getColorHexString()).isEqualTo("#0000FF");
        account.setColor("#fff");
        assertThat(account.getColor()).isEqualTo(Color.WHITE);
        assertThat(account.getColorHexString()).isEqualTo("#FFFFFF");
        account.setColor("rgb(0,255,0)");
        assertThat(account.getColor()).isEqualTo(Color.GREEN);
        assertThat(account.getColorHexString()).isEqualTo("#00FF00");
        account.setColor("rgba(255, 0, 0, 0.5)");
        assertThat(account.getColor()).isEqualTo(Color.RED);
        assertThat(account.getColorHexString()).isEqualTo("#FF0000");
        account.setColor("hsl(300, 100%, 50%)");
        assertThat(account.getColor()).isEqualTo(Color.MAGENTA);
        assertThat(account.getColorHexString()).isEqualTo("#FF00FF");
        account.setColor("hsla(0, 100%, 50%, 1.0)");
        assertThat(account.getColor()).isEqualTo(Color.RED);
        assertThat(account.getColorHexString()).isEqualTo("#FF0000");
    }
}
