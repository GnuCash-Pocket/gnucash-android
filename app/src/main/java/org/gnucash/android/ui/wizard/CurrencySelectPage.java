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

package org.gnucash.android.ui.wizard;

import androidx.annotation.NonNull;

import com.tech.freak.wizardpager.model.ModelCallbacks;
import com.tech.freak.wizardpager.model.SingleFixedChoicePage;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.model.Commodity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Page displaying all the commodities in the database
 */
public class CurrencySelectPage extends SingleFixedChoicePage {

    final Map<String, String> currenciesByLabel = new HashMap<>();

    public CurrencySelectPage(ModelCallbacks callbacks, String title) {
        super(callbacks, title);
    }

    public CurrencySelectPage setChoices() {
        currenciesByLabel.clear();
        CommoditiesDbAdapter adapter = GnuCashApplication.getCommoditiesDbAdapter();
        List<Commodity> commodities = adapter.getAllRecords();
        SortedSet<String> choices = new TreeSet<>();
        for (Commodity commodity : commodities) {
            choices.add(addCurrency(commodity));
        }
        setChoices(choices.toArray(new String[0]));
        return this;
    }

    private String addCurrency(@NonNull Commodity commodity) {
        String code = commodity.getCurrencyCode();
        String label = commodity.formatListItem();
        currenciesByLabel.put(label, code);
        return label;
    }
}
