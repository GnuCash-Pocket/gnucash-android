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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.gnucash.android.R
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.isNullOrZero
import org.gnucash.android.ui.theme.GnucashTheme

/**
 * Composable row displaying the account balance label and amount.
 *
 * @param modifier Optional [Modifier] for layout/styling
 * @param balance The [Money] balance to display
 * @param account The [Account] to determine normal balance display direction, or null for default sign logic
 */
@Composable
fun AccountBalanceRow(
    modifier: Modifier = Modifier,
    balance: Money?,
    account: Account? = null,
) {
    var displayBalance = balance
    val balanceColor: Color = if (displayBalance.isNullOrZero()) {
        MaterialTheme.colorScheme.onSurface
    } else {
        if (account != null) {
            val accountType = account.type
            if (accountType.hasDebitNormalBalance != accountType.hasDebitDisplayBalance) {
                displayBalance = -displayBalance
            }
        }
        if (displayBalance.isNegative) {
            colorResource(id = R.color.debit_red)
        } else {
            colorResource(id = R.color.credit_green)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(2f),
            text = stringResource(id = R.string.account_balance),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.size(4.dp))

        Text(
            modifier = Modifier.weight(2f),
            text = displayBalance?.formattedString().orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = balanceColor,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Preview() {
    GnucashTheme {
        Column {
            SplitAmountInfoRow(
                accountName = "Assets:Current Assets:Cash In Wallet",
                debitAmount = Money(200_000.0, Commodity.USD),
            )
            SplitAmountInfoRow(
                accountName = "Expense:Books",
                creditAmount = Money(200_000.0, Commodity.USD),
            )
            AccountBalanceRow(
                balance = Money(7899.99, Commodity.USD)
            )
        }
    }
}
