package org.gnucash.android.ui.transaction

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.gnucash.android.R
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.isNullOrZero
import org.gnucash.android.ui.theme.GnucashTheme

/**
 * Split amount row displaying the account name along with debit and credit amounts.
 *
 * @param modifier Optional [Modifier] for customizing layout or styling
 * @param accountName The account path or name (e.g., "Assets:Current Assets:Cash In Wallet")
 * @param debitAmount Formatted debit amount (e.g., "$ 2000.00")
 * @param creditAmount Formatted credit amount (e.g., "$ 2000.00")
 */
@Composable
fun SplitAmountInfoRow(
    modifier: Modifier = Modifier,
    accountName: String?,
    debitAmount: Money? = null,
    creditAmount: Money? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Account Name
        Text(
            modifier = Modifier.weight(2f),
            text = accountName.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.StartEllipsis
        )

        Spacer(modifier = Modifier.size(4.dp))

        // Debit Amount
        AmountText(
            modifier = Modifier.weight(1f),
            amount = debitAmount,
        )

        Spacer(modifier = Modifier.size(4.dp))

        // Credit Amount
        AmountText(
            modifier = Modifier.weight(1f),
            amount = creditAmount,
        )
    }
}

@Composable
private fun AmountText(
    modifier: Modifier,
    amount: Money?,
) {
    val color: Color = if (amount.isNullOrZero()) {
        MaterialTheme.colorScheme.onSurface
    } else if (amount.isNegative) {
        colorResource(id = R.color.debit_red)
    } else {
        colorResource(id = R.color.credit_green)
    }

    Text(
        text = amount?.formattedString().orEmpty(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Right,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Preview() {
    GnucashTheme {
        Column {
            SplitAmountInfoRow(
                accountName = "Assets:Current Assets:Cash In Wallet",
                debitAmount = Money(0.0, Commodity.USD),
            )
            SplitAmountInfoRow(
                accountName = "Assets:Current Assets:Cash In Wallet",
                debitAmount = Money(1000.0, Commodity.USD),
            )
            SplitAmountInfoRow(
                accountName = "Assets:Current Assets:Cash In Wallet",
                debitAmount = Money(-2000.0, Commodity.USD),
            )
            SplitAmountInfoRow(
                accountName = "Expense:Books",
                creditAmount = Money(0.0, Commodity.USD),
            )
            SplitAmountInfoRow(
                accountName = "Expense:Books",
                creditAmount = Money(3000.0, Commodity.USD),
            )
            SplitAmountInfoRow(
                accountName = "Expense:Books",
                creditAmount = Money(-4000.0, Commodity.USD),
            )
        }
    }
}