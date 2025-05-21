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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tech.freak.wizardpager.model.AbstractWizardModel;
import com.tech.freak.wizardpager.model.BranchPage;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.model.PageList;
import com.tech.freak.wizardpager.model.SingleFixedChoicePage;

import org.gnucash.android.R;
import org.gnucash.android.model.Commodity;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Wizard displayed upon first run of the application for setup
 */
public class FirstRunWizardModel extends AbstractWizardModel {

    public String titleWelcome;

    public String titleCurrency;
    public String titleOtherCurrency;
    public String optionCurrencyOther;
    private Map<String, String> currencies;

    public String titleAccount;

    public String titleFeedback;
    public String optionFeedbackSend;
    public String optionFeedbackDisable;

    public String optionAccountDefault;
    public String optionAccountImport;
    public String optionAccountUser;

    public FirstRunWizardModel(Context context) {
        super(context);
    }

    @Override
    protected PageList onNewRootPageList() {
        final Context context = mContext;

        titleWelcome = context.getString(R.string.wizard_title_welcome_to_gnucash);
        Page welomePage = new WelcomePage(this, titleWelcome);

        titleOtherCurrency = context.getString(R.string.wizard_title_select_currency);

        CurrencySelectPage otherCurrencyPage = new CurrencySelectPage(this, titleOtherCurrency)
            .setChoices();
        currencies = new HashMap<>();
        currencies.putAll(otherCurrencyPage.currencies);

        titleAccount = context.getString(R.string.wizard_title_account_setup);
        optionAccountDefault = context.getString(R.string.wizard_option_create_default_accounts);
        optionAccountImport = context.getString(R.string.wizard_option_import_my_accounts);
        optionAccountUser = context.getString(R.string.wizard_option_let_me_handle_it);

        Page accountsPage = new SingleFixedChoicePage(this, titleAccount)
            .setChoices(optionAccountDefault, optionAccountImport, optionAccountUser)
            .setValue(optionAccountDefault)
            .setRequired(true);

        titleCurrency = context.getString(R.string.wizard_title_default_currency);
        optionCurrencyOther = context.getString(R.string.wizard_option_currency_other);

        SortedSet<String> currenciesLabels = new TreeSet<>();
        String currencyDefault = addCurrency(Commodity.DEFAULT_COMMODITY);
        currenciesLabels.add(currencyDefault);
        currenciesLabels.add(addCurrency(Commodity.AUD));
        currenciesLabels.add(addCurrency(Commodity.CAD));
        currenciesLabels.add(addCurrency(Commodity.CHF));
        currenciesLabels.add(addCurrency(Commodity.EUR));
        currenciesLabels.add(addCurrency(Commodity.GBP));
        currenciesLabels.add(addCurrency(Commodity.JPY));
        currenciesLabels.add(addCurrency(Commodity.USD));

        BranchPage currencyPage = new BranchPage(this, titleCurrency);
        for (String code : currenciesLabels) {
            currencyPage.addBranch(code, accountsPage);
        }
        currencyPage.addBranch(optionCurrencyOther, otherCurrencyPage, accountsPage)
            .setValue(currencyDefault)
            .setRequired(true);

        titleFeedback = context.getString(R.string.wizard_title_feedback_options);
        optionFeedbackSend = context.getString(R.string.wizard_option_auto_send_crash_reports);
        optionFeedbackDisable = context.getString(R.string.wizard_option_disable_crash_reports);

        Page feedbackPage = new SingleFixedChoicePage(this, titleFeedback)
            .setChoices(optionFeedbackSend, optionFeedbackDisable)
            .setRequired(true);

        return new PageList(
            welomePage,
            currencyPage,
            feedbackPage
        );
    }

    private String addCurrency(@NonNull Commodity commodity) {
        String code = commodity.getCurrencyCode();
        String label = commodity.formatListItem();
        currencies.put(label, code);
        return label;
    }

    @Nullable
    public String getCurrencyByLabel(String label) {
        return currencies.get(label);
    }
}
