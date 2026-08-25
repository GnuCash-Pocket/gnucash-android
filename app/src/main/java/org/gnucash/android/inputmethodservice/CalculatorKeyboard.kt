package org.gnucash.android.inputmethodservice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import org.gnucash.android.ui.theme.GnucashTheme

@Composable
fun CalculatorKeyboard(
    onText: (String) -> Unit = {},
    onClear: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEvaluate: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        CalculatorKeypad(
            modifier = Modifier.widthIn(max = KeypadWidthWide),
            onText = onText,
            onClear = onClear,
            onDelete = onDelete,
            onEvaluate = onEvaluate
        )
    }
}

@Composable
@Preview(name = "phone-light", device = Devices.PHONE, showBackground = true, showSystemUi = true)
@Preview(name = "tablet-light", device = Devices.TABLET, showBackground = true, showSystemUi = true)
@Preview(
    name = "phone-dark",
    device = Devices.PHONE,
    showBackground = true,
    showSystemUi = true,
    uiMode = UI_MODE_NIGHT_YES
)
@Preview(
    name = "tablet-dark",
    device = Devices.TABLET,
    showBackground = true,
    showSystemUi = true,
    uiMode = UI_MODE_NIGHT_YES
)
private fun Preview() {
    GnucashTheme {
        Column(verticalArrangement = Arrangement.Bottom) {
            CalculatorKeyboard()
        }
    }
}
