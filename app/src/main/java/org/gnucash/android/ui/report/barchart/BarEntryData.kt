package org.gnucash.android.ui.report.barchart

import org.gnucash.android.ui.report.AccountColor

data class BarEntryData(
    val date: String,
    val labels: List<AccountColor>,
)
