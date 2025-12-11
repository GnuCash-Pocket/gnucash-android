package org.gnucash.android.ui.wizard

import android.content.Context
import com.tech.freak.wizardpager.model.ModelCallbacks
import com.tech.freak.wizardpager.model.SingleFixedChoicePage
import org.gnucash.android.importer.xml.AccountsTemplate

/**
 * Page displaying all the accounts templates.
 */
class AccountsSelectPage(callbacks: ModelCallbacks, title: String) :
    SingleFixedChoicePage(callbacks, title) {

    private val _accounts = mutableMapOf<String, String>()

    val accountsByLabel: Map<String, String> = _accounts
    private val templates = AccountsTemplate()

    fun setChoices(context: Context): AccountsSelectPage {
        _accounts.clear()
        for (header in templates.headers(context)) {
            val label = addExample(header)
            if (header.assetId.endsWith(templateCommon)) {
                setValue(label)
            }
        }
        setChoices(*_accounts.keys.toTypedArray())
        return this
    }

    private fun addExample(header: AccountsTemplate.Header): String {
        var label = header.title
        val description = header.shortDescription ?: header.longDescription
        if (!description.isNullOrEmpty()) {
            label += "\n" + description
        }
        _accounts[label] = header.assetId
        return label
    }

    companion object {
        private const val templateCommon = "/acctchrt_common.gnucash-xea"
    }
}
