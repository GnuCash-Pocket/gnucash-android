/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.wizard

import com.tech.freak.wizardpager.model.ModelCallbacks
import com.tech.freak.wizardpager.model.SingleFixedChoicePage
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.Commodity
import java.util.SortedSet
import java.util.TreeSet

/**
 * Page displaying all the commodities in the database
 */
class CurrencySelectPage(callbacks: ModelCallbacks, title: String) :
    SingleFixedChoicePage(callbacks, title) {
    val currenciesByLabel = mutableMapOf<String, String>()

    fun setChoices(): CurrencySelectPage {
        currenciesByLabel.clear()
        val adapter = GnuCashApplication.commoditiesDbAdapter
        val commodities = adapter!!.allRecords
        val choices: SortedSet<String> = TreeSet<String>()
        for (commodity in commodities) {
            choices.add(addCurrency(commodity))
        }
        setChoices(*choices.toTypedArray<String>())
        return this
    }

    private fun addCurrency(commodity: Commodity): String {
        val code = commodity.currencyCode
        val label = commodity.formatListItem()
        currenciesByLabel[label] = code
        return label
    }
}
