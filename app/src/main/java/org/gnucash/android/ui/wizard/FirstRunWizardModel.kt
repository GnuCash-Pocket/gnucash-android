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

/**
 * Wizard displayed upon first run of the application for setup
 */
class FirstRunWizardModel(context: Context) : AbstractWizardModel(context) {
    var titleWelcome: String? = null

    var titleCurrency: String? = null
    var titleOtherCurrency: String? = null
    var optionCurrencyOther: String? = null

    var titleAccount: String? = null
    var titleOtherAccount: String? = null

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
        titleOtherAccount = context.getString(R.string.wizard_title_default_accounts)
        optionAccountDefault = context.getString(R.string.wizard_option_create_default_accounts)
        optionAccountImport = context.getString(R.string.wizard_option_import_my_accounts)
        optionAccountUser = context.getString(R.string.wizard_option_let_me_handle_it)

        val otherAccountsPage = AccountsSelectPage(this, titleOtherAccount!!)
            .setChoices(context)

        return BranchPage(this, titleAccount)
            .addBranch(optionAccountDefault, otherAccountsPage, feedbackPage)
            .addBranch(optionAccountImport, feedbackPage)
            .addBranch(optionAccountUser, feedbackPage)
            .setRequired(true)
    }

    private fun createCurrencyPage(context: Context): Page {
        val accountsPage = createAccountsPage(context)

        titleOtherCurrency = context.getString(R.string.wizard_title_select_currency)

        titleCurrency = context.getString(R.string.wizard_title_default_currency)
        optionCurrencyOther = context.getString(R.string.wizard_option_currency_other)

        val otherCurrencyPage = CurrencySelectPage(this, titleOtherCurrency!!)
            .setChoices(context)

        val currencyPage = DefaultCurrencyPage(this, titleCurrency!!)
            .setChoices()
        for (currencyCode in currencyPage.currenciesByLabel.keys) {
            currencyPage.addBranch(currencyCode, accountsPage)
        }
        currencyPage.addBranch(optionCurrencyOther, otherCurrencyPage, accountsPage)
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

    fun getCurrencyByLabel(label: String?): String? {
        val pages = currentPageSequence

        val page1 = pages.first { it is DefaultCurrencyPage } as DefaultCurrencyPage
        val currency1 = page1.currenciesByLabel[label]
        if (currency1 != null) return currency1

        val page2 = pages.first { it is CurrencySelectPage } as CurrencySelectPage
        val currency2 = page2.currenciesByLabel[label]
        if (currency2 != null) return currency2

        return null
    }

    fun getAccountsByLabel(label: String?): String? {
        val pages = currentPageSequence
        val page = pages.first { it is AccountsSelectPage } as AccountsSelectPage
        return page.accountsByLabel[label]
    }
}
