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

import android.content.Context
import com.tech.freak.wizardpager.model.AbstractWizardModel
import com.tech.freak.wizardpager.model.BranchPage
import com.tech.freak.wizardpager.model.Page
import com.tech.freak.wizardpager.model.PageList
import com.tech.freak.wizardpager.model.SingleFixedChoicePage
import org.gnucash.android.R
import org.gnucash.android.model.Commodity
import java.util.SortedSet
import java.util.TreeSet

/**
 * Wizard displayed upon first run of the application for setup
 */
class FirstRunWizardModel(context: Context?) : AbstractWizardModel(context) {
    var titleWelcome: String? = null

    var titleCurrency: String? = null
    var titleOtherCurrency: String? = null
    var optionCurrencyOther: String? = null
    private var currencies: MutableMap<String, String>? = null
    private var accounts: MutableMap<String, String>? = null

    var titleAccount: String? = null

    var titleFeedback: String? = null
    var optionFeedbackSend: String? = null
    var optionFeedbackDisable: String? = null

    var optionAccountDefault: String? = null
    var optionAccountImport: String? = null
    var optionAccountUser: String? = null

    override fun onNewRootPageList(): PageList {
        val context = mContext

        return PageList(
            createWelcomePage(context),
            createCurrencyPage(context)
        )
    }

    private fun createWelcomePage(context: Context): Page {
        titleWelcome = context.getString(R.string.wizard_title_welcome_to_gnucash)
        return WelcomePage(this, titleWelcome!!)
    }

    private fun createAccountsPage(context: Context): Page? {
        val feedbackPage = createFeedbackPage(context)

        titleAccount = context.getString(R.string.wizard_title_account_setup)
        optionAccountDefault = context.getString(R.string.wizard_option_create_default_accounts)
        optionAccountImport = context.getString(R.string.wizard_option_import_my_accounts)
        optionAccountUser = context.getString(R.string.wizard_option_let_me_handle_it)

        val otherAccountsPage = AccountsSelectPage(this, optionAccountDefault!!)
            .setChoices(context)
        // Called before field initialized.
        accounts = mutableMapOf<String, String>()
        accounts!!.putAll(otherAccountsPage.accountsByLabel)

        val accountsPage = BranchPage(this, titleAccount)
            .addBranch(optionAccountDefault, otherAccountsPage, feedbackPage)
            .addBranch(optionAccountImport, feedbackPage)
            .addBranch(optionAccountUser, feedbackPage)
        return accountsPage.setRequired(true)
    }

    private fun createCurrencyPage(context: Context): Page {
        val accountsPage = createAccountsPage(context)

        titleOtherCurrency = context.getString(R.string.wizard_title_select_currency)

        titleCurrency = context.getString(R.string.wizard_title_default_currency)
        optionCurrencyOther = context.getString(R.string.wizard_option_currency_other)

        val otherCurrencyPage = CurrencySelectPage(this, titleOtherCurrency!!)
            .setChoices()
        // Called before field initialized.
        currencies = mutableMapOf<String, String>()
        currencies!!.putAll(otherCurrencyPage.currenciesByLabel)

        val currenciesLabels: SortedSet<String> = TreeSet<String>()
        val currencyDefault = addCurrency(Commodity.DEFAULT_COMMODITY)
        currenciesLabels.add(currencyDefault)
        currenciesLabels.add(addCurrency(Commodity.AUD))
        currenciesLabels.add(addCurrency(Commodity.CAD))
        currenciesLabels.add(addCurrency(Commodity.CHF))
        currenciesLabels.add(addCurrency(Commodity.EUR))
        currenciesLabels.add(addCurrency(Commodity.GBP))
        currenciesLabels.add(addCurrency(Commodity.JPY))
        currenciesLabels.add(addCurrency(Commodity.USD))

        val currencyPage = BranchPage(this, titleCurrency)
        for (code in currenciesLabels) {
            currencyPage.addBranch(code, accountsPage)
        }
        currencyPage.addBranch(optionCurrencyOther, otherCurrencyPage, accountsPage)
            .setValue(currencyDefault)
            .setRequired(true)

        return currencyPage
    }

    private fun createFeedbackPage(context: Context): Page? {
        titleFeedback = context.getString(R.string.wizard_title_feedback_options)
        optionFeedbackSend = context.getString(R.string.wizard_option_auto_send_crash_reports)
        optionFeedbackDisable = context.getString(R.string.wizard_option_disable_crash_reports)

        return SingleFixedChoicePage(this, titleFeedback)
            .setChoices(optionFeedbackSend, optionFeedbackDisable)
            .setRequired(true)
    }

    private fun addCurrency(commodity: Commodity): String {
        val code = commodity.currencyCode
        val label = commodity.formatListItem()
        currencies!![label] = code
        return label
    }

    fun getCurrencyByLabel(label: String?): String? {
        return currencies!![label]
    }

    fun getAccountsByLabel(label: String?): String? {
        return accounts!![label]
    }
}
