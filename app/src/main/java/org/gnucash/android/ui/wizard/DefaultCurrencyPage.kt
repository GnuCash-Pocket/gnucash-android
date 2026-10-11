package org.gnucash.android.ui.wizard

import com.tech.freak.wizardpager.model.BranchPage
import com.tech.freak.wizardpager.model.ModelCallbacks
import org.gnucash.android.model.Commodity
import java.util.SortedSet
import java.util.TreeSet

class DefaultCurrencyPage(callbacks: ModelCallbacks, title: String) : BranchPage(callbacks, title) {
    val currenciesByLabel = mutableMapOf<String, String>()

    fun setChoices(): DefaultCurrencyPage {
        currenciesByLabel.clear()

        val choices: SortedSet<String> = TreeSet()
        val currencyDefault = addCurrency(Commodity.DEFAULT_COMMODITY)
        choices.add(currencyDefault)
        choices.add(addCurrency(Commodity.AUD))
        choices.add(addCurrency(Commodity.CAD))
        choices.add(addCurrency(Commodity.CHF))
        choices.add(addCurrency(Commodity.EUR))
        choices.add(addCurrency(Commodity.GBP))
        choices.add(addCurrency(Commodity.JPY))
        choices.add(addCurrency(Commodity.USD))

        setChoices(*choices.toTypedArray<String>())
        setValue(currencyDefault)
        return this
    }

    private fun addCurrency(commodity: Commodity): String {
        val code = commodity.currencyCode
        val label = commodity.formatListItem()
        currenciesByLabel[label] = code
        return label
    }
}